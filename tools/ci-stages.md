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

- ★ **承認の確認は `coverage.yaml` を見ない**。固定 SHA の `g1_ci_verify.sh` を実行し、
  生成された `build/spec-reconcile-report.json` の
  **`g1.complete == true`** と **`provenance.validator_source_kind == "external-pin"`**
  を確認する（`coverage.yaml` の `review` は常に `PENDING_REVIEW` のまま）
- 7: `NOT_OBSERVABLE` 以外の全 obligation が 1 件以上のテストケースを持つ
- 8〜19: テスト定義と実装の整合（YAML ↔ `TestCaseImpl`）
- 20b・20c: `CapabilityBranchTest` / outcome→Verdict 変換
- 21〜28: 生成物の一致、golden fixture、outbox 規約、依存仕様の版固定

## 実体（`.github/workflows/g1.yml`）

| job | trigger | ネットワーク | 内容 |
|---|---|---|---|
| `g1-check` | PR / push | 不要 | `g1_docgen.py --check` + 構造規則のみ（原文未取得に由来する FAIL は除外） |
| `spec-reconcile` | push / 定期 / 手動 | **必要** | 原文と全 22 仕様を強制再取得して照合 |
| `g1b-approval` | `vars.G1_TOOLS_COMMIT` が設定されているとき | 必要 | 署名済み承認の検証。**固定 SHA から runner を取り出して隔離実行**し、`g1.complete` と `provenance.validator_source_kind == "external-pin"` を確認 |

`g1b-approval` は **`tools/g1_ci_verify.sh` を呼ばず、同等の処理を workflow に展開している**。
ラッパー自身も改変されうるため、**CI 設定側に置くことが最後の trust anchor** になる。

必要な repository variables:

| 変数 | 内容 |
|---|---|
| `G1_TOOLS_COMMIT` | runner / validator の取得元（40 桁完全 SHA）。**承認時に決めて設定する** |
| `G1_ALLOWED_SIGNERS` | `gpg.ssh.allowedSignersFile` の内容（承認者の公開鍵） |

**`.github/` と `tools/g1_*` は `.github/CODEOWNERS` で保護し、
branch protection で「CODEOWNERS のレビュー必須」にすること。**
これをしないと、workflow を書き換えるだけでゲート全体が無効になる。

## 実装状況

| ステージ | 実体 | 状態 |
|---|---|---|
| `g1Check` | `tools/g1_validate.py --offline` の構造検査部（SR-15〜SR-29, SR-36）+ `tools/g1_docgen.py --check` | 通る |
| `specReconcile` | `tools/g1_validate.py`（**強制再取得**で原文と全 22 仕様を照合） | **50/51 PASS / blocking 0**（承認後は 51/51） |
| `releaseCheck` | 未実装。テストケースが 0 件のため（G2 完了後） | 未実施 |

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
           （coverage.yaml は編集しない）
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

**validator 自身を保護対象に含めている**が、それだけでは足りない。
**改変された validator を実行すると、その validator は自分の改変を報告しない**（自己検査の限界）。

### ★ 承認の検証は `tools/g1_trusted_verify.py` から行う

```bash
python3 tools/g1_trusted_verify.py [--offline]
#   0 = ブロッキング違反なし / 1 = あり / 2 = 検証の前提が崩れている
```

このランナーは**現在の checkout の validator を実行しない**。

1. **自分自身を `python -I` で起動し直す**（`PYTHONPATH` と user site を無効化）。
   これをしないと `PYTHONPATH=. python tools/g1_trusted_verify.py` で
   リポジトリ直下の未署名 `yaml.py` が署名検証**前に**実行される
2. 承認記録を最後に変更した commit A を git から特定する
3. A の署名を検証する（`signed-commit` は `git verify-commit`、
   `signed-tag` は `git verify-tag` + tag が A を指すことの確認）
4. **A が対象 commit C の子孫**であることを確認する
5. **`C..A` の変更が `tests/approvals/g1.yaml` だけ**であることを確認する
6. ★ **validator は A から取らない**。`G1_VALIDATOR_COMMIT`（CI が外部から固定する
   trust anchor。**40 桁完全 SHA のみ**。`HEAD` / `main` などの可変 ref は拒否）が
   あればそこから、なければ **C**（レビュアーが実際に読んだ成果物）から
   `g1_validate.py` / `g1_extract.py` を隔離ディレクトリに取り出す
7. `python -I` で実行し、検査対象リポジトリは `G1_REPO_ROOT` で渡す

> **なぜ A から取ってはいけないか**: A の署名者が承認記録と一緒に validator を
> 弱体化できてしまう。実際、A に「即座に `51/51 PASS` を出して終了する validator」を
> 含めて署名すると、署名検証を通ったうえで `exit 0` になった。
>
> C から取る場合も、C 自体を署名者が作れる余地は残る。
> **CI では `G1_VALIDATOR_COMMIT` を外部設定で固定すること。**

### ★ ランナー自身の固定 — `tools/g1_ci_verify.sh`

ランナー内の制約（`C..A` の制限、validator の取得元固定）は、
**ランナーを書き換えれば削除できる**。ランナーの中ではこれを防げない。

```bash
G1_TOOLS_COMMIT=<40桁SHA> tools/g1_ci_verify.sh [--offline]
```

このラッパーは固定 SHA から `g1_trusted_verify.py` / `g1_validate.py` / `g1_extract.py`
を取り出し、`python -I` で実行する。検査対象は `G1_REPO_ROOT` で渡すため、
**隔離ディレクトリではなく実リポジトリ**が検査される。

- `G1_TOOLS_COMMIT` 未設定 → **exit 2**（fail closed）
- `HEAD` / `main` などの可変 ref → **exit 2**
- ★ **validator の取得元は常に `G1_TOOLS_COMMIT`**。環境に残った
  `G1_VALIDATOR_COMMIT` は `env -u` で落とす（ambient 継承で
  「runner は正しいが validator だけ別 commit」になるのを防ぐ）。
  別 anchor を使う場合は `--validator-commit=<40桁SHA>` を**明示**する
  （警告を出力し、`provenance.validator_source` に記録される）
- 実測: 現在の checkout の runner を「即時 PASS」に書き換えても、
  ラッパー経由なら `BLOCK`（SR-38 / SR-40）

**最後の一枚は CI 設定側で固定する。** ラッパー自身もリポジトリ内のコピーなので、
CI では下のスニペットを workflow に**インラインで**書くか、固定 SHA から取り出して実行する。

```yaml
# .github/workflows 等（例）
- name: G1 approval verification
  env:
    G1_TOOLS_COMMIT: "0000000000000000000000000000000000000000"   # ← 承認時に固定
  run: |
    set -euo pipefail
    TMP=$(mktemp -d); mkdir -p "$TMP/tools"
    for f in tools/g1_trusted_verify.py tools/g1_validate.py tools/g1_extract.py; do
      git show "$G1_TOOLS_COMMIT:$f" > "$TMP/$f"
    done
    env -u PYTHONPATH G1_REPO_ROOT="$PWD"         G1_RUNNER_COMMIT="$G1_TOOLS_COMMIT"         G1_VALIDATOR_COMMIT="$G1_TOOLS_COMMIT"         python3 -I "$TMP/tools/g1_trusted_verify.py"
```

監査レポートの `provenance` に `validator_source` / `validator_source_kind`
（`external-pin` / `target-commit`）/ `runner_source` / `repo_root` が記録される。

### 承認時に `coverage.yaml` を編集しない

`C..A` を承認記録だけに制限した結果、**承認では `g1_state` も編集しない**。
完了状態は承認記録から導出する（レポートの `g1.state` は導出値、
`g1.authored_state` が `coverage.yaml` の記載）。

**shadow import の遮断**: python は `sys.path[0]` にスクリプトの位置を入れるため、
未追跡の `tools/yaml.py` を置くだけで署名検証より前に任意コードが走る。
ランナーは冒頭で自分のディレクトリを `sys.path` から外し、
抽出した validator は `tools/` を `sys.path` に一切載せない
（`g1_extract` は明示パスで `importlib` 読み込み）。
併せて **SR-40**（`tools/` に未追跡 `.py` がない）と
**`tools/` のファイル集合の一致**で、shim の設置自体を検出する。

**限界の明示**（validator はこれ以上を主張しない）:

| 保証できること | 保証できないこと |
|---|---|
| 署名鍵の保持者が承認記録に署名した | その鍵が**実在のレビュアー**のものか（`allowedSignersFile` / CODEOWNERS などリポジトリ運用側の設定に依存） |
| 承認後に保護対象ファイルが変わっていない | **ランナー自身**が改変された場合。→ `tools/g1_ci_verify.sh` が固定 SHA からランナーを取り出す（下記） |
| レビュアーが原文を読んだと**記録した**こと | レビュアーが**実際に**原文を読んだこと |

**G1 完了の判定式**（レポートの `g1.complete`）:

```
complete = (blocking failure が 0)
       AND (open question が 0)
       AND (全 obligation が tests/approvals/g1.yaml で承認済み)
# coverage.yaml は承認時に編集しない（C..A の制限のため編集できない）
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
