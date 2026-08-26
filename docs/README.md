# Samlier — SAML Conformance Test Suite

**設計ドキュメント** / 作成日: 2026-08-25 / ステータス: Draft（Phase 1 設計中）

任意の SAML IdP / SP 実装を、公開された仕様上の要求に基づいて誰でも同じ条件で検証できる OSS。
OIDF Conformance Suite の SAML 版に相当するものを目指す。

## 決定済み事項

| 項目 | 決定 |
|---|---|
| プロダクト名 | **Samlier**（repo `github.com/sgrastar/samlier` / package `org.samlier.*` / image `samlier/suite`） |
| ライセンス | **Apache-2.0**（DCO、CLA なし） |
| v0.1 のスコープ | **Phase 1 完全** — IIP v1.1 全 69 要件、SLO / ECP を含む |
| 結果公開の信頼モデル | **Level 0（ローカルエクスポート）+ Level 2（Hosted 実行のみ共有 URL）**。self-hosted 結果のアップロードは不採用 |
| バックエンド | **Java 21 + Javalin/Jetty + OpenSAML 5 + Apache Santuario + SQLite** |
| フロントエンド | **React + Vite (TypeScript)**。`report.html` も同じアプリから静的ビルド |
| Hosted 版の管理アクセス | **Run ごとのシークレット URL**（Phase 1）→ 将来 Authrim による OIDC ログイン |
| リファレンス実装の結果 | **バージョン固定のサンプルとして公開**。CI では回すが常時公開はしない |
| ビルド / リポジトリ | **Gradle (Kotlin DSL)** / **単一リポジトリ** |
| 仕様原文の引用 | **ID + 自作要約 + 原文アンカーへのリンク**。全文転載はしない（Kantara への照会は並行） |
| 多言語 | **英語のみ**。テスト定義 YAML に `ja` のキーだけ用意し、CI 必須は `en` |
| 要件カタログ | **`tests/coverage.yaml` を正**とし、`04` の表はそこから生成 |

**残る未決事項は D-15（Hosted 版の運用: ドメイン・ホスティング先・費用負担）のみ**で、M4 着手前までに決めれば足ります。
決定の経緯は [09-open-decisions.md](09-open-decisions.md) を参照。

## 設計ゲート G1 の状態

**作成フェーズ完了・レビュー待ち（`PENDING_REVIEW`）**

| 成果物 | 内容 |
|---|---|
| `tests/specs.yaml` | 仕様カタログ（22 仕様。外部ドラフトは版を固定） |
| `tests/coverage.yaml` | **要件カタログ＝判定レベルの唯一の出典**。69 要件 → **133 義務**（うち 25 は複数範囲の `source_clauses`） |
| `tests/predicates.yaml` | 条件述語の固定集合（8 述語） |
| `build/spec-reconcile-report.json` | 独立 validator の結果（**50/51 PASS・ブロッキング 0**。残り 1 は「未承認」＝ G1 完了条件） |
| `docs/04-requirement-coverage.md` | `coverage.yaml` からの**生成物**（手編集禁止） |
| `tools/ci-stages.md` | ゲートごとの CI ステージと trust anchor の所在 |
| `.github/workflows/g1.yml` | 実体の CI（`g1-check` / `spec-reconcile` / `g1b-approval`） |
| `.github/CODEOWNERS` | trust anchor になるファイルの保護 |
| `tools/g1_{author,docgen,validate,extract}.py` | 生成 / 文書化 / **独立検証** / 共有の正規化モジュール |
| `tools/g1_{trusted_verify.py,ci_verify.sh}` | 承認検証の信頼された実行入口と CI ラッパー |
| `tools/requirements.txt` | 固定依存（PyYAML 6.0.2 / pdfminer.six 20240706） |

作成者は `reviewer` / `approved_at` を埋めていません。
**承認は `coverage.yaml` に書きません** — 正本は署名済みの `tests/approvals/g1.yaml`
（承認対象 commit の外）です。

## 実装までのゲート

```
G1a  カタログ作成        ✅ 完了（PENDING_REVIEW）
  ↓
G1b  義務の意味レビュー   ⏳ 作成者以外が原文と coverage.yaml を照合し、署名済み承認記録を作る
  ↓                        検証: G1_TOOLS_COMMIT=<SHA> tools/g1_ci_verify.sh
M0   骨格実装            G1b 後に着手してよい（Test Peer / Transcript / Preflight）
  ↓
G2   テスト設計          ⏳ 132 義務をケース ID に割り当て、対照ケースと反例を定義
  ↓                        作成者以外が設計をレビューして承認
M1〜 判定ケースの実装     ★ G2 完了後
```

**G1b と G2 は別のレビュー**です。G1b は「義務が原文と正しく対応しているか」、
G2 は「ケースに検出力があるか」を見ます。
過去のレビューで原文照合 49 件中 41 件に誤りが出た領域なので、
どちらも作成者以外による承認を必須にしています。

検出力の証明は **mutant peer**（既知の違反を注入した Test IdP/SP）で行い、
リファレンス実装の結果差は使いません（[00 §5](00-concept.md)）。

## ドキュメント一覧

| # | ドキュメント | 内容 |
|---|---|---|
| 00 | [concept.md](00-concept.md) | 何を作るのか / 作らないのか、既存ツールとの差分、成功条件 |
| 01 | [scope-and-roadmap.md](01-scope-and-roadmap.md) | Phase 1〜5 の定義と各 Phase の完了条件 |
| 02 | [architecture.md](02-architecture.md) | システム構成、技術スタック、Test Peer 設計 |
| 03 | [test-model.md](03-test-model.md) | Test Plan / Test Case / 実行モード / 判定語彙 |
| 04 | [requirement-coverage.md](04-requirement-coverage.md) | Kantara IIP v1.1 全 69 要件のテスト可能性マッピング |
| 05 | [test-definition-format.md](05-test-definition-format.md) | テスト定義 YAML のスキーマ |
| 06 | [results-and-publication.md](06-results-and-publication.md) | 結果フォーマット、共有 URL、信頼モデル |
| 07 | [deployment-and-networking.md](07-deployment-and-networking.md) | Docker、URL/TLS 要件、Hosted 版 |
| 08 | [suite-security.md](08-suite-security.md) | Suite 自身のセキュリティ（SSRF 等） |
| 09 | [open-decisions.md](09-open-decisions.md) | 意思決定ログ（D-01〜D-15） |
| 10 | [memo-review.md](10-memo-review.md) | 元構想メモのレビュー結果（矛盾・欠落・改良点） |
| 11 | [review-log.md](11-review-log.md) | 設計レビューの記録と反映結果 |

## 30 秒サマリ

- **対象仕様（Phase 1）**: Kantara Initiative *SAML V2.0 Implementation Profile for Federation Interoperability* **v1.1 (2019-12-18)**
- **要件数**: Common 31 + SP 17 + IdP 21 = **IdP Profile 52 / SP Profile 48**
- **やり方**: テスト対象の反対側（Test SP / Test IdP）を Suite が演じるブラックボックステスト
- **実行形態**: Docker 単一イメージ。Hosted 版も self-hosted 版も同じイメージ
- **結果**: Requirement ID 単位の PASS/FAIL と、仕様根拠→送受信 XML→判定理由の追跡
- **公開**: opt-in の共有 URL。ただし「Certified」とは絶対に言わない

## 最重要の設計判断（Phase 1）

0. **「適用されない」と「検証できなかった」を厳密に分ける**。実行環境の都合で試験できない
   MUST 義務は `NOT_VERIFIED` であって `NOT_APPLICABLE` ではない。母数に残り、Run は
   `conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる。Run の判定は**適合性と実行完全性の二軸**で出す。
   判定レベルは**義務（obligation）単位**で `coverage.yaml` だけが持ち、
   テスト定義にも実装にも書かせない。→ [03](03-test-model.md), [05](05-test-definition-format.md)

1. **Test Plan = 1 つの entityID**。SAML には動的クライアント登録がないため、テストケースごとに entityID を変えると利用者が数十回の手動登録を強いられる。1 Test Plan につき 1 つの「全部入りメタデータ」を発行し、ケース切替は ACS index / RelayState / 事前アーミングで行う。→ [02](02-architecture.md)
2. **判定は 3 系統**。Automated（バックチャネルのみ）/ Browser-assisted（利用者のブラウザ経由）/ Attested（対象側の挙動を利用者が申告）。SAML のブラックボックステストは「相手が拒否したこと」を機械的に観測できない場合があり、これを設計に組み込まないと結果の数字が意味を持たない。→ [03](03-test-model.md)
3. **メタデータ系要件はテスト対象側の再設定を要求する**。IIP-MD01〜MD04 などは「対象が Suite のメタデータを取得しに来る」構成でないと検証できない。Test Plan にメタデータ配布方式（manual / HTTP / MDQ）を持たせる。manual の場合これらは
**`NOT_VERIFIED(plan_configuration)`** になる（`NOT_APPLICABLE` **ではない**）。母数に残り、`conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる。→ [04](04-requirement-coverage.md)
