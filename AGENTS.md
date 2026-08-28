# AGENTS.md — Samlier の実装エージェント向け規約

Samlier は SAML 実装の適合性を判定するツールである。
**誤った判定は他人の製品を不当に「不適合」と表示する**ため、
この規約は「動くこと」より優先される。

設計の全体像は [`docs/README.md`](docs/README.md)。判断に迷ったら実装より先に設計文書を読む。

---

## 絶対に守ること

### 1. 承認済みの G1 成果物を編集しない

```
tests/coverage.yaml   tests/specs.yaml   tests/predicates.yaml   tests/approvals/*
```

これらは**署名済みの承認記録に digest で拘束されている**。
1 バイトでも変えると承認が失効する（`tools/g1_validate.py` の SR-25c / SR-36 / SR-38）。

判定レベル（MUST / SHOULD / MAY）を変えたくなったら、それは**仕様解釈の変更**であり、
コードではなく G1 のやり直し（原文照合 → 再承認）が必要。**勝手に直さない。**

### 2. 生成物を手編集しない

| 生成物 | 生成元 | コマンド |
|---|---|---|
| `docs/04-requirement-coverage.md` | `tests/coverage.yaml` | `tools/g1_docgen.py` |
| `build/spec-reconcile-report.json` | validator の実行結果（**Git 管理外**。正本は CI artifact） | `tools/g1_validate.py` |
| ドキュメント中の `result.json` 例 | `Evaluator` の golden fixture | （M1 で実装） |
| ドキュメント中の母数（義務数・要件数など） | `tests/coverage.yaml` | `tools/g1_docgen.py` |

`tools/g1_docgen.py --check` が CI で差分を検出する。

**母数は本文に直書きしない。** `<!--g1:obligations-->427<!--/g1-->` のようにマーカーで書き、
`g1_docgen.py` に埋めさせる。説明のための架空の数を書くときは行に `<!--g1-literal-->` を置く。
直書きは `g1_validate.py` の **SR-41** が検出して FAIL にする
（義務を足したときに複数ファイルの数値が取り残される事故を防ぐため）。

### 3. ケースは Verdict を返さない

ケース実装が返すのは **`outcome`**（`satisfied` / `satisfied_with_note` / `violated` /
`indeterminate` / `inconsistent` / `not_verified`）だけ。

`PASS` / `FAIL` / `WARNING` への変換は **`Evaluator` が `coverage.yaml` の
`level` を見て一元的に行う**（[docs/05 §2.3](docs/05-test-definition-format.md)）。

> ここを破ると **SHOULD 義務を FAIL にする**。実際に一度やらかしている
> （[docs/11 の R10](docs/11-review-log.md)）。ケース側に `Verdict` を返す型を作らない。

### 4. 送信は outbox のみ

ケースは対象へ直接 HTTP を送らない。`OutboundAction` を返し、Runner が outbox で実行する
（[docs/05 §4.3](docs/05-test-definition-format.md)）。

- `actionId` は `CaseState` から**決定論的に導出**する。
  `UUID.randomUUID()` / `System.nanoTime()` は使わない
- 配信不明（`UNKNOWN_DELIVERY`）を**対象の FAIL にしない**。
  再送で replay エラーが返っても、それは Suite 側の不確実性である
- 再送可否はケースが宣言しない。`OutboundKind` の allowlist で Runner が決める

### 5. 「適用されない」と「検証できなかった」を混同しない

| | 使ってよい場合 |
|---|---|
| `NOT_APPLICABLE` | **役割違い**、または**条件付き義務の条件が偽**。この 2 つだけ |
| `NOT_VERIFIED(reason)` | それ以外の「実行できなかった」全て。母数に残り、MUST なら Run は未完了 |

実行環境の都合で試験できない MUST を `NOT_APPLICABLE` にすると、
**構成を選ぶだけで MUST の検証を回避できてしまう**。

### 6. 原文にない条件・閾値を足さない

仕様に数値がない要件（クロックスキュー等）に Samlier 独自の絶対閾値を入れない。
実務的に伝えたい観測は **advisory**（`affects_verdict: false`）にする
（[docs/04 の Advisory 節](docs/04-requirement-coverage.md)）。

### 7. 対照のないケースを作らない

「この期待値を満たすが義務は満たしていない実装」が作れるなら、そのケースに検出力はない。
positive / negative control を必ず対にする（[docs/01 の G2](docs/01-scope-and-roadmap.md)）。

**`linked_obligations` の展開分も覆う。** `kind: inherit_variants` のリンクは
「リンク先の `required_variants` も覆え」という意味で、**推移的**に展開する。
`role` / `level` / `condition` / `testability` は**継承しない**（義務自身の値を使う）。
覆っても**リンク先義務の網羅にはならない**（二重計上しない）。
規則の全文は [docs/03 §リンクの意味](docs/03-test-model.md)。

### 8. 生のリクエストを壊さない

HTTP-Redirect バインディングの署名検証は **URL デコード前のクエリ文字列のバイト列**が対象。
パースして再構成すると検証が壊れ、**正しい実装を「署名不正」と誤判定する**
（[docs/02 §3.5](docs/02-architecture.md)）。

### 9. 資格情報を永続化しない

ECP の HTTP Basic 認証情報は Run スコープのメモリのみ。
`CaseState` にも outbox の payload にも Transcript にも書かない。
`Authorization` / `Cookie` は **Recorder への投入前**に不可逆に除去する
（[docs/02 §5.2](docs/02-architecture.md)）。

---

## 変更ごとに実行する検証

```bash
# 依存（初回のみ）
python3 -m venv .venv
.venv/bin/pip install --require-hashes -r tools/requirements.lock

# 常に
.venv/bin/python tools/g1_docgen.py --check        # 生成物の一致
.venv/bin/python tools/g1_validate.py --structural-only   # 構造規則（ネットワーク不要）

# tests/ や tools/g1_* を触ったとき
.venv/bin/python tools/g1_validate.py              # 原文と全参照仕様を照合（ネットワーク要）

# 承認後
G1_TOOLS_COMMIT=<40桁SHA> PY=.venv/bin/python tools/g1_ci_verify.sh
```

`--structural-only` がブロッキング違反を返したら、**その変更は入れない**。

---

## 作業の順序（ゲート）

```
G1a ✅ カタログ作成
G1b ⏳ 義務の意味レビュー（作成者以外が原文と照合し署名承認）
M0     骨格実装        ← G1b 後に着手してよい。テスト 0 件
G2  ⏳ テスト設計       ← ケース定義・対照・mutant。作成者以外がレビュー
M1〜   判定ケース実装   ← ★ G2 完了後
```

**M1 以降を G2 の前に始めない。**
過去に原文照合 49 件中 41 件の誤りが出ており、
「義務は正しいがケースに検出力がない」という失敗が最も起きやすい段階である。

---

## 実装スタック

| 層 | 選択 |
|---|---|
| 言語 | Java 21 |
| Web | Javalin + Jetty（生リクエストへのアクセスが必要なため） |
| SAML | OpenSAML 5.x（正常系）+ 生 DOM/StAX（異常系。Phase 4 の足場） |
| XML Security | Apache Santuario |
| DB | SQLite（Transcript はファイル、DB には参照のみ） |
| フロント | React + Vite。`report.html` は同じアプリの静的ビルド |
| ビルド | Gradle (Kotlin DSL) |

`docs/02-architecture.md` のコード構成に従う。
特に **`peer/`（テスト用・検証が緩い）と `auth/`（管理用・厳格）を混ぜない**
（[docs/09 D-09](docs/09-open-decisions.md)）。

---

## 迷ったときに読む順

1. [`docs/03-test-model.md`](docs/03-test-model.md) — 判定語彙・集約規則・共通判定手順
2. [`docs/05-test-definition-format.md`](docs/05-test-definition-format.md) — テスト定義と実装インタフェース
3. [`docs/02-architecture.md`](docs/02-architecture.md) — Test Peer 設計・ECP・Transcript
4. [`docs/11-review-log.md`](docs/11-review-log.md) — **過去に何を間違えたか**。同じ轍を踏まないため
