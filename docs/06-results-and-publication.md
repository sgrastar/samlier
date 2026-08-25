# 06. 結果フォーマットと公開

## 1. 結果 JSON（スキーマ v1）

Suite の最も長寿命な成果物。破壊的変更にはバージョンを上げる。

> ⚠ **以下は形の説明であり、値は生成物に置き換わります。**
> 前回のレビューで、ここに置いていた手書きの例が
> **自分自身の判定規則と矛盾している**ことが指摘されました
> （`CONFORMANT_WITH_WARNINGS` なのに未解決の MUST がある、
> IdP の Run に SP 専用義務が並ぶ、`not_observable` キーが重複している）。
>
> [03 §7.5](03-test-model.md) の決定に従い、ドキュメント中の例は
> **`Evaluator` の出力を golden fixture として生成**し、
> JSON Schema (`schema/result-v1.json`) と CI で検証します
> （[05 §5](05-test-definition-format.md) の規則 23〜25）。
> 生成に切り替わるまで、ここには**構造のみ**を置き、整合した数値は載せません。

### 1.1 トップレベル構造

```jsonc
{
  "schema_version": "1",

  "run": { "id", "started_at", "finished_at",
           "conformance",           // CONFORMANT | CONFORMANT_WITH_WARNINGS
                                    // | CONFORMANT_WITH_DECLARED_EXCLUSIONS
                                    // | NON_CONFORMANT | INDETERMINATE
           "completeness",          // COMPLETE | INCOMPLETE
           "scope_qualifications" },// 申告のみの除外の機械可読な記録（[03 §1]）
  //  ★ 2 つは別の軸。片方だけの表示を禁止する（[03 §7.2]）
  //  ★ 必ず Evaluator.evaluate() が算出する。手で入れない

  "suite":   { "name", "version", "image_digest", "execution_mode" },

  "evaluation_bundle": {              // ★ 判定の正本（レビュー指摘 10）
    "digest": "sha256:…",             //   下記すべてを含む合成ダイジェスト
    "components": {
      "coverage_yaml":        "sha256:…",   // 義務・レベル・条件の正本
      "test_definitions":     "sha256:…",   // defs/*.yaml
      "specs_yaml":           "sha256:…",   // 仕様カタログ（外部ドラフトの版を含む）
      "outcome_mapping_version": "1",       // outcome × level → Verdict 規則
      "aggregation_policy_version": "1"     // 重大度順序・Run 判定規則
    }
  },

  "profile": { "id", "spec": { "document", "version", "date" },
               "level_definition_note" },

  "target":  { "declared_product", "declared_by", "verified": false,
               "entity_id", "metadata_digest", "role",
               "kind" },            // idp | sp | token_translation_proxy ★
  //  role ∈ idp | sp   ★ この Run に現れる義務は必ずこの role に適用されるものだけ

  "configuration": { "suite_metadata_delivery", "reachability",
                     "declared_features", "parameters" },

  "applicability": [                  // ★ Evaluator の入力そのもの（[03 §6.2]）
    { "obligation": "IIP-SP15.a",
      "predicate": "supports_single_logout",
      "predicate_kind": "CAPABILITY_BASED",   // CLAIM_BASED | CAPABILITY_BASED | CLASSIFICATION_BASED
      "declared": true,                        // 利用者の申告値（null = 申告なし）
      "observed": true,                        // 観測から導いた値（null = 材料なし）
      "effective_result": "TRUE",              // TRUE | FALSE | UNKNOWN  ← ケース実行の可否
      "conflict": false,                       // ★ effective_result と独立。true なら INCONSISTENT
      "basis": "observed",                     // declared | observed | declaration_only_exclusion
      "evidence": [ { "kind": "metadata", "xpath": "…" } ] }
  ],

  "advisories": [                     // ★ 原文に根拠のない観測。判定に影響しない
    { "code", "obligation", "severity", "message_en", "affects_verdict": false }
  ],

  "suite_incidents": [                // ★ Suite 側の障害。対象の評価とは別枠
    { "kind": "UNKNOWN_DELIVERY", "case": "IIP-IDP13-02",
      "action_id": "…", "note": "配信の可否を確定できなかった" }
  ],

  "summary":  { "requirements": {…}, "obligations": {…}, "cases": {…} },
  "coverage": { … },                  // [03 §7.4] の定義に一致すること

  "requirements": [ { "id", "verdict", "spec_url",
                      "obligations": [ { "key", "level", "role", "verdict" } ],
                      "cases": [ { "id", "obligation", "outcome", "verdict",
                                   "mode", "reason_code", "reason",
                                   "attested", "evidence", "definition_url" } ] } ],

  "unresolved":     [ { "obligation", "level", "verdict", "reason", "how_to_resolve" } ],
  "not_observable": [ { "obligation", "level", "reason" } ],   // ★ キーは 1 回だけ

  "conformance_statement": "…"        // UI が必ずそのまま表示する定型文
}
```

### 1.2 スキーマが強制する不変条件

JSON Schema と `ResultInvariantTest` の両方で検証する。

| # | 不変条件 |
|---|---|
| 1 | `run.conformance` / `run.completeness` が `Evaluator` の出力と一致する（[03 §7.2](03-test-model.md)） |
| 2 | `coverage.must_unresolved > 0` なら `run.conformance ∈ {INDETERMINATE, NON_CONFORMANT}` |
| 3 | `summary.obligations.fail > 0` なら `run.conformance = NON_CONFORMANT` |
| 3b | 選択プロファイル内に未解決義務があれば `run.completeness = INCOMPLETE`（レベルを問わない） |
| 4 | **`requirements[].obligations[].role` が全て `target.role` と一致する**（IdP の Run に SP 専用義務が現れない） |
| 5 | `coverage.verified_ratio = must_resolved / must_observable`（[03 §7.4](03-test-model.md)） |
| 6 | `not_observable` / `unresolved` は空でも必ず存在し、**キーの重複がない** |
| 7 | `unresolved` の全要素に `how_to_resolve` がある |
| 8 | `declared_features` で未対応と申告された MUST 義務が `NOT_SUPPORTED` になっていない（FAIL のはず） |
| 8b | `reason_code: capability_absent` を持つケースの `outcome` が `violated` であり、その義務の verdict が `obligation.level` に対応する値（MUST→FAIL / SHOULD→WARNING / MAY→NOT_SUPPORTED）になっている。**SHOULD 義務が FAIL になっていない**。[03 §4](03-test-model.md) |
| 9 | `applicability` に条件付き義務が全件記録されている。`effective_result` ∈ `{TRUE, FALSE, UNKNOWN}`、`conflict` は独立した boolean。**`CONFLICT` という値は存在しない**（廃止済み） |
| 9b | `applicability[].conflict = true` の義務は、集約入力に `INCONSISTENT` が**注入されている**こと（`effective_result` の値を問わない）。★ 最終 verdict が `INCONSISTENT` と**等しいとは限らない** — 同じ義務に `FAIL` のケースがあれば重大度順序（[03 §6.1](03-test-model.md)）により `FAIL` が正しい。検証は「verdict の重大度が `INCONSISTENT` **以上**であること」 |
| 9d | `basis = "declaration_only_exclusion"` の件数が `coverage.excluded_by_declaration` と一致し、`conformance_statement` に明記され、`run.scope_qualifications[]` に `reason` / `attested_by` / `attested_at` つきで記録されている |
| 9e | `predicate_kind ∈ {CAPABILITY_BASED, CLASSIFICATION_BASED}` かつ `observed = null` かつ `declared = false` の項目の `effective_result` が `FALSE` でない（`declaration_only_exclusion` を除く） |
| 9f | ★ `coverage.excluded_by_declaration > 0` なら `run.conformance ∉ {CONFORMANT, CONFORMANT_WITH_WARNINGS}`（`CONFORMANT_WITH_DECLARED_EXCLUSIONS` 以上になる） |
| 9g | `advisories[].affects_verdict` が全て `false`。advisory を除いて再計算した `run.conformance` / `run.completeness` / `coverage` が結果と一致する |
| 9c | ★ `UNKNOWN_DELIVERY` が発生した **当該 CaseRun** の `outcome` が `violated` でなく `verdict` が `FAIL` でない。**義務全体は対象外**（同じ義務の別ケースが違反を明確に証明していれば、集約規則どおり義務は `FAIL` が正しい） |
| 10 | `evaluation_bundle.digest` が `components` から決定論的に再計算できる |

### 1.3 設計上のポイント

- `requirements` / `obligations` / `cases` の **3 つの粒度**でサマリを持つ。
  判定レベルが付くのは義務単位なので、`obligations` が実質的な母数になる
- `coverage` は必須。**適合ラベルと同じ大きさで表示する**（[03 §7.4](03-test-model.md)）
- `unresolved` / `not_observable` は**空でも必ず出す**。隠す経路を作らない
- `conformance_statement` は UI・公開ページ・`report.html` が**必ずそのまま表示する**文字列。
  **これも golden fixture から生成する**（手書きの例を置かない）
- `suite_incidents` は Suite 自身の障害の記録。**対象の評価に混ぜない**
  （[05 §4.3.1](05-test-definition-format.md)）
- `target.verified: false` — 製品名は自己申告であることを構造に埋め込む
- **`evaluation_bundle.digest` が再現性の要**。Suite のバージョンだけでは足りない。
  判定は `coverage.yaml` の level / condition から導かれるため、
  カタログが変われば同じコードでも結論が変わる。
  外部ドラフト（SAML-EC 等）を参照する義務があるため、`specs_yaml` には
  **参照仕様の版**まで含める（[02 §3.7](02-architecture.md)）

## 2. 出力形式

| 形式 | 用途 |
|---|---|
| `result.json` | 機械可読。CI やアーカイブ用。**これが正**。`Evaluator` の出力そのもの |
| `report.html` | 単一ファイルの自己完結 HTML（画像・CSS 埋込）。オフラインで配れる |
| `transcript.zip` | 全 HTTP / SAML メッセージの生データ。デバッグ用 |
| バッジ SVG | `Tested: IIP v1.1 IdP Core — 41/45` 形式。Phase 2 |

## 3. 結果の共有と信頼モデル ★ 元メモの最大の穴

元メモは「Test Run 終了後、共有 URL を発行できる。デフォルトでは非公開」としているが、
**self-hosted で誰でも動かせるツールの結果を、そのまま公開 URL にできる**ようにすると、
結果は簡単に偽造できる。「PASS 74 / FAIL 0」の JSON を手で書けば済む。

この Suite の価値は「再現可能なテスト結果そのものを品質証明にする」ことなので、
**偽造可能性は設計の根幹に関わる**。

### 3 つの信頼レベルを明示的に区別する

```
┌──────────────────────────────────────────────────────────────┐
│ Level 0 — LOCAL                                              │
│   self-hosted 環境での実行結果。ローカルファイルとしてのみ存在 │
│   → JSON / HTML をエクスポートできる                          │
│   → 「自己申告」であることがファイル自身に記載される           │
├──────────────────────────────────────────────────────────────┤
│ Level 1 — ATTESTED UPLOAD   ❌ 不採用                         │
│   self-hosted の結果を Hosted 版にアップロードして URL 化      │
│   → 捏造した JSON をアップロードでき、Level 2 の結果まで       │
│      「どうせ自己申告だろう」と見られてしまうため採用しない    │
├──────────────────────────────────────────────────────────────┤
│ Level 2 — HOSTED RUN                                         │
│   公式 Hosted 版で実行された結果                              │
│   → Suite 側が実行過程（Transcript）を保持している            │
│   → これのみ「検証済みの実行」と表示できる                    │
└──────────────────────────────────────────────────────────────┘
```

**決定（[09 D-04](09-open-decisions.md)）: Level 0 と Level 2 のみを実装する。**
Level 1 は偽造されたアップロードを見分けられないため採用しない。

含意:
- self-hosted の利用者は結果を共有 URL にできない。`report.html`（自己完結ファイル）を自分で配る
- **社内 IdP をテストしたい層は共有 URL を使えない**。この非対称性は README で明示する
- Hosted 版の運用が Phase 1 の成果物に含まれる（[09 D-15](09-open-decisions.md)）

### 公開ページの表示

> ⚠ **ここに置いていたワイヤーフレームの数値例は削除しました。**
> レビューで、`Resolved 45 / 47` かつ `NOT_VERIFIED 2` なのに `CONFORMANT` と
> 表示している矛盾が指摘されました（未解決の MUST が 2 件ある以上
> `conformance = INDETERMINATE` が正しい）。
> **手書きの例は 4 回連続で不整合を生んでいます。**
> 公開ページの表示例も `Evaluator` の golden fixture から生成します。

公開ページが**必ず含める項目**（値ではなく項目の規定）:

| 項目 | 規定 |
|---|---|
| `Conformance` | `run.conformance` をそのまま。**`Completeness` と必ず併記**（[03 §7.2](03-test-model.md)） |
| `Completeness` | `run.completeness` と未解決件数 |
| `Resolved` | `must_resolved / must_observable` を**分数で**表示。比率単独では出さない |
| 未検証・検証不能 | `NOT_VERIFIED` と `NOT_OBSERVABLE` の件数を必ず表示 |
| 申告のみの除外 | `excluded_by_declaration > 0` なら、件数・理由・「検証されていない」旨を**目立つ位置**に |
| `conformance_statement` | そのまま全文表示 |
| 製品名 | `(self-declared)` を付す |
| 定型文 | `This is a test result, not a certification.` |
| 構成 | `declared_features` / `parameters` / `suite_metadata_delivery` / `reachability` |
| 版 | Suite version と `evaluation_bundle.digest` |

### 表現に関する規約（Phase 1 で凍結）

**使ってよい語**
- `Tested against SAML V2.0 Implementation Profile for Federation Interoperability v1.1`
- `Conformance Test Result`
- `Test Report`

**使ってはいけない語**
- `Certified` / `Certification`
- `Compliant` / `Compliance`（試験結果の要約としては可、称号としては不可）
- `Approved` / `Endorsed` / `Validated by <組織名>`
- Kantara / OASIS の名称を**認定主体であるかのように**使うこと

この規約は README と公開ページのフッターに恒久的に記載する。

## 4. 公開結果に含めてはいけないもの

`publish` 時に自動でスクラブする。

| 対象 | 扱い |
|---|---|
| Transcript の全文 | **既定で非公開**。Hosted 版が内部保持するのみ。opt-in で公開可 |
| 対象の IP アドレス / 内部ホスト名 | マスク |
| テスト用ユーザーの ID / パスワード（IIP-IDP14 の ECP 認証情報） | **一切保存しない**。実行中のみメモリ保持 |
| Cookie / Authorization ヘッダ | ★ **公開時ではなく Transcript 投入前に不可逆に除去済み**（[02 §5.2](02-architecture.md)）。ここでのマスクは二重の安全弁 |
| Assertion 中の属性値 | 既定でマスク。実 IdP の実ユーザー属性が入りうる |
| `Test Plan` の `test_user_hint` | 公開しない |

> 実 IdP でテストすると **実在ユーザーの氏名・メールアドレスが Assertion に入る**。
> これを気づかず公開する事故が最も起きやすい。既定マスク + 公開前プレビューで防ぐ。
>
> **資格情報はこの表に頼らない。** 公開時のスクラブでは、非公開の Run でも
> `/data` やバックアップに平文相当（Base64）で残ってしまう。
> `Authorization` / `Cookie` / パスワード相当のフォーム値は
> **Recorder への投入前**に除去する（[02 §5.2](02-architecture.md)）。

## 5. 保持期間（Hosted 版）

| データ | 既定保持 |
|---|---|
| 非公開の Run | 30 日 |
| 公開された Run の結果 | 無期限（利用者が削除可能） |
| 公開された Run の Transcript | 90 日 |
| 削除要求 | 発行時のシークレット URL 経由で本人が削除できる |
