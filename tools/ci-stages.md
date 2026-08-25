# CI ステージの分離

**G1 用検査とリリース用 CI は別ジョブにする。**
G1 の段階ではテストケース実装がまだ存在しないため、
ケース実装を要求する規則を同じジョブに入れると G1 が永久に通らない。

| ステージ | いつ走るか | ネットワーク | 何を検査するか |
|---|---|---|---|
| **`g1Check`** | 全 PR（現在の主ジョブ） | 不要 | カタログの構造だけ。[05 §5](../docs/05-test-definition-format.md) の規則 1〜6c-0、20d |
| **`specReconcile`** | 定期 + リリース前 | **必要** | 原文を取得し、節/句のダイジェストと語の検査。規則 5b-3・5b-4・6c-1 |
| **`releaseCheck`** | `release` / `publish` / `dockerPush` の前 | 不要 | **ケース実装を要求する規則**。7〜19、20b・20c、21〜28 |

```
tasks.named("release")    { dependsOn(":specReconcile", ":releaseCheck") }
tasks.named("publish")    { dependsOn(":specReconcile", ":releaseCheck") }
tasks.named("dockerPush") { dependsOn(":specReconcile", ":releaseCheck") }
```

## `g1Check` に含める規則（G1 作成フェーズで通る必要がある）

- 1〜5d: カタログの構造・述語・条件・`configuration_failure_semantics`
- 6b: 全 obligation に `review` ブロックがある（`state: PENDING_REVIEW` を許容）
- 6c / 6c-0: `source_spec` / `source_selector` / `source_section_digest` / `source_clause` の存在と範囲
- 6: `NOT_OBSERVABLE` の obligation が理由文を持ち、ケースを持たない
- 20d: 条件付き義務の適用性評価がケース実行より先

## `releaseCheck` に回す規則（G1 完了後に有効になる）

- 6b の厳格版: **`reviewer` が非 null かつ作成者と異なる**、`approved_at` がある
- 7: `NOT_OBSERVABLE` 以外の全 obligation が 1 件以上のテストケースを持つ
- 8〜19: テスト定義と実装の整合（YAML ↔ `TestCaseImpl`）
- 20b・20c: `CapabilityBranchTest` / outcome→Verdict 変換
- 21〜28: 生成物の一致、golden fixture、outbox 規約、依存仕様の版固定

## 実装状況

| ステージ | 実体 | 状態 |
|---|---|---|
| `g1Check` | `tools/g1_validate.py --offline` の構造検査部（SR-15〜SR-29, SR-36）+ `tools/g1_docgen.py --check` | 通る |
| `specReconcile` | `tools/g1_validate.py`（**強制再取得**で原文と全 22 仕様を照合） | **49/50 PASS / blocking 0**（承認後は 50/50） |
| `releaseCheck` | 未実装。テストケースが 0 件のため | 未実施 |

`build/spec-reconcile-report.json` の `checks[]` は、どの検査がブロッキングかを
`totals.blocking_failures` で区別する。**SR-30（open question 残存）と SR-31（未承認）は
G1 の完了条件**であり、作成フェーズでは FAIL のまま提出される。
それ以外の FAIL は成果物の欠陥を意味し、`g1_validate.py` は終了コード 1 を返す。

### ★ 承認は「対象 commit の外にある署名付き記録」に拘束される

`obligation_digest` は事故による改変を検出するが、**digest を計算し直せる者**には無力である。
そこで承認の真正性は **git の署名**でのみ担保する。

```
commit C : tests/{coverage,specs,predicates}.yaml     ← 承認対象（全て PENDING_REVIEW）
commit A : tests/approvals/g1.yaml                    ← 承認記録。★ C の外・署名必須
           coverage.yaml の g1_state を APPROVED に
```

**承認記録を承認対象の中に置いてはならない。** 記録を追記した時点で対象 commit が
変わってしまい、正常な手順では決して一致しない（自己参照）。

`tests/approvals/g1.yaml`:

```yaml
target_commit: <40 桁の完全な SHA-1>          # 短縮 SHA は拒否する
artifact_digests:
  tests/coverage.yaml:   "sha256:…"          # 対象 commit の内容の digest
  tests/specs.yaml:      "sha256:…"
  tests/predicates.yaml: "sha256:…"
evidence:
  kind: signed-commit
  reviewers: [<承認者>]
  evidence_url: https://…                     # PR / レビュー記録
  # ref は置かない。自分を含む commit の SHA を書くのは自己参照になる
approvals:
  - obligation: IIP-G01.a
    obligation_digest: "sha256:…"             # 対象 commit の内容から再計算した値
    reviewer: <承認者>                         # authored_by と異なること
    approved_at: 2026-08-26T12:00:00+00:00    # タイムゾーン必須の ISO-8601
```

validator（**SR-38**）が確認すること:

| 確認 | 手段 |
|---|---|
| 承認記録が commit されている | `git log -1 -- tests/approvals/g1.yaml` |
| **保護対象ファイルの現在値が A と一致する** | `git show <A>:<path>` とバイト比較 |
| **`tests/` のファイル集合が A と一致する** | `git ls-tree -r A tests` |
| **その commit が署名されている** | `git verify-commit` |
| **正本は署名済み commit の中身**（作業ツリーではない） | `git show <C_sig>:tests/approvals/g1.yaml` |
| 作業ツリーが署名済み内容と一致する | digest 比較 |
| `target_commit` が 40 桁で git に実在する | `git rev-parse --verify <sha>^{commit}` の完全一致 |
| 対象 commit の成果物 digest が一致する | `git show <C>:tests/*.yaml` |
| 全義務が承認され、digest が対象 commit の内容と一致する | 対象 commit から再計算 |
| reviewer ≠ authored_by / `evidence.reviewers` に含まれる | 対象 commit の `authored_by` と照合 |
| `approved_at` がタイムゾーン付き ISO-8601 | 文字列全体を `fromisoformat` |

### 承認が守る対象（`PROTECTED_PATHS`）

承認記録だけを署名で守っても意味がない。**署名済み A の tree と現在値を突き合わせる対象**:

```
tests/coverage.yaml      tests/specs.yaml       tests/predicates.yaml
tests/approvals/g1.yaml  tools/g1_validate.py   tools/g1_extract.py
```

加えて **`tests/` 配下のファイル集合**が A と一致することも確認する（追加・削除の検出）。
これがないと、A の後に coverage を書き換えて `obligation_digest` を再計算するだけで通ってしまう。

**validator 自身を保護対象に含めている**のは、検査器を弱める改変を検出するためである。

**限界の明示**（validator はこれ以上を主張しない）:

| 保証できること | 保証できないこと |
|---|---|
| 署名鍵の保持者が承認記録に署名した | その鍵が**実在のレビュアー**のものか（`allowedSignersFile` / CODEOWNERS などリポジトリ運用側の設定に依存） |
| 承認後に保護対象ファイルが変わっていない | **改変された validator を実行した場合**の結果（自己検査の原理的限界。CI では承認済み commit から checkout した validator を使うこと） |
| レビュアーが原文を読んだと**記録した**こと | レビュアーが**実際に**原文を読んだこと |

**G1 完了の判定式**（レポートの `g1.complete`）:

```
complete = (blocking failure が 0)
       AND (open question が 0)
       AND (全 obligation が tests/approvals/g1.yaml で承認済み)
       AND (coverage.yaml の g1_state == "APPROVED")
```

`state: APPROVED` に書き換えるだけでは通らない。**SR-36** が
reviewer / approved_at の存在、reviewer ≠ authored_by、
承認時に記録した spec / version / selector / 節ダイジェストが**現在値と一致する**ことを要求する
（原文が変われば節ダイジェストが変わり、承認は自動的に失効する）。

## 依存環境

```bash
python3 -m venv .venv
.venv/bin/pip install -r tools/requirements.txt   # PyYAML 6.0.2 / pdfminer.six 20240706
.venv/bin/python tools/g1_validate.py             # 既定 = 強制再取得（network）
.venv/bin/python tools/g1_validate.py --offline   # キャッシュのみ。CI の g1Check 用
```

## 取得セマンティクス

`g1_extract.fetch()` の `mode`:

| mode | 挙動 | 使う場所 |
|---|---|---|
| `network`（既定） | **必ず再取得**してキャッシュを更新する | `specReconcile` / リリース前 |
| `offline` | キャッシュのみ。未キャッシュなら失敗 | `g1Check`（オフライン CI） |
| `cache-first` | キャッシュがあれば使う | 起票（`g1_author.py`）のみ |

キャッシュ優先を既定にすると、**URL が到達不能でも古い内容で PASS してしまう**。
既定を強制再取得にしたうえで、到達不能な URL では `SR-00` / `SR-33` が落ちることを実地確認済み。

## pin する原文は「再取得で再現する」ものに限る

`G1_VERIFY_STABILITY=1 python3 tools/g1_author.py` は、pin した全仕様を
**2 回取得してバイト列が一致すること**を検証する。

動的レンダリングされる `tools.ietf.org` の HTML と OASIS の errata HTML は
取得のたびにバイト列が変わり digest を固定できなかったため、
**不変のアーカイブ URL（IETF は `www.ietf.org/archive/id/*.txt`、OASIS errata は PDF）**に切り替えた。
