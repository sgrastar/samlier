# 03. テストモデル

## 1. データモデル

```
Profile          静的。IIP v1.1 IdP Core / IdP Full / SP Core / SP Full
  └─ Requirement 静的。IIP-G01 … IIP-IDP21（69 件）
       └─ Obligation ★ 静的。1 要件の中の「役割 × 条件 × RFC2119 レベル」単位の義務
            └─ TestCase  静的。1 Obligation に 0..N ケース。YAML + 実装クラス

TestPlan         利用者が作る。Profile + Target + 構成宣言 + 発行された Test Peer 鍵/entityID
  └─ TestRun     1 回の実行
       └─ CaseRun  ケース 1 件の実行結果（Verdict + 根拠 + Transcript 参照）

判定は CaseRun → Obligation → Requirement(役割別) → Run の順に集約する（§6, §7）
```

### ★ なぜ Obligation 層が要るのか

1 つの要件 ID の中に、**役割ごとに異なるレベル**や**条件付きの義務**が同居している。
`applies_to: [idp, sp]` と単一の `level` では正しい FAIL / WARNING を導出できない。

| 要件 | 原文の構造 |
|---|---|
| IIP-MD01 | *Identity Providers **MUST** and Service Providers **SHOULD** support … the Metadata Query Protocol* |
| IIP-MD10 | *… Identity providers **MUST** and Service Providers **SHOULD** limit the use of algorithms* |
| IIP-SP13 | *Service Providers **MUST** support the ability to reject unsigned `<samlp:Response>` elements and **SHOULD** do so by default* |
| IIP-SP14 | *Service Providers **SHOULD** support the … SingleLogout profile. Service Providers **that claim support for this profile MUST** be capable of issuing logout requests* |
| IIP-ALG08 | *… **MUST** support the ability to prevent the use of particular algorithms … The set of such algorithms **MUST** be configurable and it is **RECOMMENDED** that the default set include …* |
| IIP-MD09 | *… **MUST** be capable of publishing the cryptographic capabilities … It is **RECOMMENDED** that they support dynamic generation* |

したがって要件を義務に分解する。

```yaml
# tests/coverage.yaml
- id: IIP-SP13
  obligations:
    - key: IIP-SP13.a
      roles: [sp]
      level: MUST
      condition: null
      summary_en: "Support the ability to reject unsigned <samlp:Response> elements"
    - key: IIP-SP13.b
      roles: [sp]
      level: SHOULD
      condition: null
      summary_en: "Reject unsigned <samlp:Response> elements by default"

- id: IIP-SP14
  obligations:
    - key: IIP-SP14.a
      roles: [sp]
      level: SHOULD
      summary_en: "Support the SAML V2.0 SingleLogout profile"
    - key: IIP-SP14.b
      roles: [sp]
      level: MUST
      condition: declared_features.single_logout == true   # ★ 条件付き MUST
      summary_en: "If claiming SLO support, be capable of issuing logout requests"
```

- `condition` は**三値**で評価する（下記 ★）。偽なら **`NOT_APPLICABLE`**（母数から除外）
- 判定レベルは**義務単位**で決まる。`IIP-SP13.a` を満たせなければ FAIL、
  `IIP-SP13.b`（既定で拒否）を満たさなければ **WARNING**（FAIL ではない）
- 要件の Verdict は、その役割に適用される義務の集約（§6）

### ★ 条件の評価: `effective_result` と `conflict` を分離する

`condition` を Test Plan の自己申告だけで評価すると、
**「SLO は未対応です」と申告するだけで条件付き MUST を回避できてしまう**。

評価は **2 つの独立した値**を出す。1 つに畳むと、
「実行すべきか」と「矛盾があるか」が区別できなくなる。

```
effective_result ∈ { TRUE, FALSE, UNKNOWN }   → ケースを実行するか（スケジューリング）
conflict         ∈ { true, false }            → 申告と観測が食い違っているか
```

| `effective_result` | ケース | 義務への入力 |
|---|---|---|
| `TRUE` | 実行する | （なし。通常どおりケースを集約） |
| `FALSE` | 実行しない | `NOT_APPLICABLE` |
| `UNKNOWN` | 実行しない | `NOT_VERIFIED(applicability_undetermined)` |

`conflict = true` は **`effective_result` と独立に** `INCONSISTENT` を義務の集約に注入する。
重大度順序上 `INCONSISTENT` は `NOT_APPLICABLE` より上位なので、
**矛盾したまま義務が黙って除外されることはない**。

| declared | observed | effective_result | conflict | 結果 |
|---|---|---|---|---|
| true | true | `TRUE` | false | 実行 |
| false | false | `FALSE` | false | `NOT_APPLICABLE` |
| **false** | **true** | `TRUE`（観測優先） | **true** | 実行 + `INCONSISTENT` |
| **true** | **false** | `FALSE`（観測優先） | **true** | 実行しない + `INCONSISTENT` |
| true / false | 材料なし | 述語の種類による（下記） | false | — |
| 材料なし | 材料なし | `UNKNOWN` | false | `NOT_VERIFIED` |

### ★ 述語の種類によって「申告だけ」の扱いを変える

観測材料が得られなかったとき、申告をそのまま採用してよいかは**条件の性質による**。
一律に採用すると、`target.kind = token_translation_proxy` や
`outbound_encryption: false` を選ぶだけで MUST を除外できてしまう。

| 種類 | 条件の性質 | 例 | 観測材料がないときの `FALSE` 申告 |
|---|---|---|---|
| `CLAIM_BASED` | **「対応を表明しているか」そのものが条件** | IIP-SP14.b *SPs **that claim support** for this profile* | ✅ **`FALSE` を採用**。申告が真理値そのものだから |
| `CAPABILITY_BASED` | 実際の能力が条件 | IIP-MD08 *implementations **that support outbound encryption*** | ❌ **`UNKNOWN`** → `NOT_VERIFIED(applicability_undetermined)`。申告だけで能力の不在を証明できない |
| `CLASSIFICATION_BASED` | 製品の分類が条件 | IIP-IDP13 *does not apply to **token translation Proxies*** | ❌ 既定は **`UNKNOWN`**（下記の例外あり） |

`CLASSIFICATION_BASED` は再実行しても観測材料が増えないため、
`UNKNOWN` のままでは永久に完全なレポートを作れない。そこで**明示的な除外申告**の経路だけを開ける。

```
利用者が「この製品は token translation Proxy である」と
明示的な除外申告（チェックボックス + 理由の記入）を行った場合に限り、
effective_result = FALSE を採用する。ただし:

  - applicability[].basis = "declaration_only_exclusion" を記録する
  - coverage.excluded_by_declaration に計上する
  - run.scope_qualifications[] に機械可読な記録を残す
  - ★ run.conformance が CONFORMANT / CONFORMANT_WITH_WARNINGS にならない
```

### ★ 申告のみの除外があった Run は `CONFORMANT` を返さない

`conformance_statement` に注記するだけでは不十分である。
**バッジや API の利用者は `run.conformance` しか読まない。**
それでは「token translation Proxy です」と申告するだけで ECP の MUST を除外した
`CONFORMANT` を機械的に取得できてしまう。

そこで **enum の値そのものに現れるようにする**。

```
run.conformance ∈ {
    CONFORMANT,
    CONFORMANT_WITH_WARNINGS,
    CONFORMANT_WITH_DECLARED_EXCLUSIONS,   ★ 新設
    NON_CONFORMANT,
    INDETERMINATE
}

excluded_by_declaration > 0 かつ他が全て充足
    → CONFORMANT_WITH_DECLARED_EXCLUSIONS   （WARNING の有無を問わない）
```

`run.conformance == "CONFORMANT"` で分岐する素朴な利用者は**この値に一致しない**。
第 2 のフィールドを読み飛ばされる問題が起きない。

併せて `run.scope_qualifications[]` に機械可読な詳細を残す。

```json
"scope_qualifications": [
  { "kind": "declared_exclusion",
    "predicate": "is_token_translation_proxy",
    "target_kind": "token_translation_proxy",
    "excluded_obligations": ["IIP-IDP13.a", "IIP-IDP13.b"],
    "reason": "<利用者が記入した理由>",
    "attested_by": "<利用者の識別子 or 'anonymous'>",
    "attested_at": "2026-08-25T04:12:03Z",
    "verified": false }
]
```

`target.kind` も結果 JSON に必ず含める（[06 §1](06-results-and-publication.md)）。

### ★ 除外範囲は原文の除外文が属する要件だけ

上の例で除外されるのは **IIP-IDP13 の義務だけ**である。
*This requirement does not apply to token translation Proxies.* は
**IIP-IDP13 の末尾にある文**であり、IIP-IDP14〜16 には及ばない。
IDP14（HTTP Basic）・IDP15（SAML-EC の鍵）・IDP16（ECP 設定のメタデータ取り込み）は
**無条件の MUST** である。

- `excluded_obligations` は**手で列挙しない**。
  `coverage.yaml` で当該 `condition` を持つ義務を Evaluator が機械的に集める
- ある除外述語が複数の要件にまたがる場合、**各要件の原文に除外文があること**を
  `:specReconcile` が原文を取得して確認する（[04 設計ゲート G1](04-requirement-coverage.md)）
- CI 規則 5b-4: 同じ `predicate` を持つ義務が、**原文該当節に除外文を含まない要件**に
  付いていたら落とす

> 除外の範囲が 1 要件でも広がると、**無条件 MUST を自己申告だけで消せる**ようになる。
> 除外述語の付与範囲は G1 のレビュー対象そのものである。

**黙って除外できる経路は作らない。** 除外は可能だが、必ず結果の最上位に現れる。

### 観測材料

各条件は **申告値と観測事実の両方**から評価する。

| 条件 | 種類 | 申告 | 観測（優先） |
|---|---|---|---|
| `claims_single_logout` | `CLAIM_BASED` | `declared_features.single_logout` | — |
| `supports_single_logout` | `CAPABILITY_BASED` | 同上 | 対象メタデータの `<SingleLogoutService>` / 実際の LogoutRequest 発行 |
| `supports_outbound_encryption` | `CAPABILITY_BASED` | `declared_features.assertion_encryption` | 実際に `<EncryptedAssertion>` / `<EncryptedID>` を生成したか |
| `supports_ecp` | `CAPABILITY_BASED` | `declared_features.ecp` | 対象メタデータの `SingleSignOnService` に SOAP バインディングがあるか |
| `is_token_translation_proxy` | `CLASSIFICATION_BASED` | `target.kind` | — （明示的な除外申告のみ） |

規則:

1. **観測が申告と矛盾したら `conflict = true`**。`effective_result` は観測を優先する
2. 観測材料がない場合の扱いは**述語の種類**による（上表）
3. 適用性の判定根拠（`declared` / `observed` / `effective_result` / `conflict` / `basis`）は
   結果 JSON に**全件**記録する（[06 §1](06-results-and-publication.md)）
4. `ApplicabilityEvaluation` は `Evaluator.evaluate()` の明示的な入力である（§6.2, §7.5）

### ★ リンクの意味 — `linked_obligations`

原文が **別の節の規則をそのまま取り込む**ことがある。
例: IIP-IDP16（ECP, §2.3.10）の冒頭は Browser SSO **§4.1.6 の規則を継承**する。
これを注記だけで表すと、G2 でケースを起こすときに継承分が落ちる。
そこで `coverage.yaml` に**機械可読なリンク**を持たせる。

```yaml
linked_obligations:
  - obligation: IIP-SSO06.a
    kind: inherit_variants          # 現時点で定義されている唯一の種別
    note_ja: "…なぜ取り込むのか…"
```

**意味の定義**（G2 / 実装はこの通りに扱うこと）:

| # | 規則 | 理由 |
|---|---|---|
| **L1** | `kind: inherit_variants` は「**A のケースは B の `required_variants` も覆わなければならない**」。展開は**推移的** | 継承分をケース設計から落とさないため |
| **L2** | **`role` / `level` / `condition` / `testability` は継承しない**。常に A 自身の値を使う | A は別の文脈（ECP / SLO 等）で成立する義務。B の条件や役割を持ち込むと誤適用になる |
| **L3** | 展開後の variant は **`<obligation-key>#<variant-id>`** で参照する（`covers_variants` の記法） | どの義務由来の variant かを判別できるようにするため |
| **L4** | **二重計上しない**。A のケースが B の variant を覆っても **B の網羅は満たされない**（逆も同じ）。completeness / mutant coverage の母数は**義務単位**で数える | 「A のケースがあるから B も済み」を防ぐ |
| **L5** | ケースの digest には**展開後の variant ID 集合**を含める。B の variant を編集すると A のケースの digest も変わり、**再レビューが必要**になる | B の変更が A のケースに黙って波及するのを防ぐ |
| **L6** | `docs/04` は A 側に「**参照取り込み**」、B 側に「**被参照**」を出力する | 片方向だけだと B の編集者が影響範囲に気づけない |

**CI で強制していること**（`tools/g1_validate.py`）:

| 検査 | 内容 |
|---|---|
| `SR-22g-shape` | `{obligation, kind, note_ja}` の形であること |
| `SR-22d` | 参照先が実在すること |
| `SR-22e` | 自己参照でないこと |
| `SR-22f` | 循環がないこと |
| `SR-22g` | `kind` が定義済みの語彙（現在は `inherit_variants` のみ）であること |
| `SR-22h` | 展開が有限（深さ 4 以内）で、空でないこと |
| `SR-22i` | 取り込み先が `NOT_OBSERVABLE` でないこと（variant がなくリンクが無意味になる） |

> **種別を増やすときは、`docs/03`（この表）→ `g1_author.py` の `LINK_KINDS` →
> `g1_validate.py` の `SR-22g` を同時に更新する。**
> 意味の定義がない `kind` が成果物に入ることを禁じている。

## 2. Test Plan の構成項目

元メモの UI 案は「Profile / metadata URL / オプション」だけだったが、
これでは実行できないテストが多数出る。実際に必要な項目は次の通り。

```yaml
name: "Keycloak 26 IdP"
profile: idp-full                  # idp-core | idp-full | sp-core | sp-full

target:
  kind: idp | sp | token_translation_proxy   # ★ 適用性の判定に使う
  #  token_translation_proxy を選ぶと IIP-IDP13 は NOT_APPLICABLE になる
  #  （原文: "This requirement does not apply to token translation Proxies."）
  entity_id: "https://kc.example.org/realms/test"
  metadata_source:
    kind: url | mdq | upload
    location: "https://kc.example.org/realms/test/protocol/saml/descriptor"

# Suite のメタデータを対象にどう渡すか ★ 多くのメタデータ系テストの前提条件
suite_metadata_delivery:
  kind: manual | http_url | mdq
  # manual  = 利用者が XML をダウンロードして対象に貼り付ける
  #           → IIP-MD01〜MD04 は NOT_VERIFIED(plan_configuration)。
  #             NOT_APPLICABLE ではない（母数に残り、Run は
  #             conformance=INDETERMINATE / completeness=INCOMPLETE になる）
  # http_url= 対象が Suite の metadata URL を定期取得する
  # mdq     = 対象が Suite の MDQ を引く（IIP-MD01 の検証に必須）

declared_features:               # 対象が実装していると申告する任意機能
  single_logout: true
  ecp: false
  assertion_encryption: true
  encrypted_nameid: false
  idp_discovery: false           # SP のみ
  accepts_unsolicited_sso: true  # SP のみ。false ならアーミング方式にフォールバック

parameters:
  clock_skew_tolerance_seconds: 180   # 「reasonable」の解釈値。結果に必ず記録する
  metadata_refresh_wait_seconds: 300  # IIP-MD02 の待ち時間
  test_user_hint: "testuser / ログイン方法のメモ（結果には出さない）"

interaction:
  allow_browser_steps: true      # false ならバックチャネルのみのテストに限定
  allow_attestation: true        # false なら ATTESTED なケースは
                                 # NOT_VERIFIED(interaction_disallowed)。
                                 # INDETERMINATE は「実行したが証拠不足」の場合に限る
```

> **重要**: `declared_features` と `parameters` は **結果に必ず刻む**。
> これがないと 2 つの結果を比較できない。「PASS 74」だけの数字には意味がない。

## 3. テスト可能性の分類（5 系統）

SAML のブラックボックステストは、全てを機械的に観測できるわけではない。
元メモに完全に欠けていた点なので、明示的にモデル化する。
この分類は **義務ごと**に `coverage.yaml` に記録する（[05 §2.1](05-test-definition-format.md)）。

| モード | 説明 | 例 |
|---|---|---|
| `AUTOMATED` | Suite が対象と直接やりとりして完結する。ブラウザ不要 | メタデータの静的検査、MDQ、SOAP SLO、ECP |
| `BROWSER` | 利用者のブラウザが SAML のユーザーエージェントとして必要 | Web Browser SSO 全般 |
| `ATTESTED` | 対象内部の挙動が外から見えないため、利用者に構造化された質問をして答えてもらう | 「SP はエラーを表示し、ログインセッションを作らなかったか?」 |
| `CONFIG` | 対象側の設定変更を利用者に依頼したうえで `AUTOMATED`/`BROWSER` を行う | Suite のメタデータを再読込させる、属性リリース設定を変える |
| `NOT_OBSERVABLE` | 外部からは原理的に検証できない。テストを作らず、レポートに理由付きで出す | 「persistent NameID に意味を持たせていない」（IIP-SP12） |

### ATTESTED の扱い方

```
┌──────────────────────────────────────────────┐
│ IIP-SP13-02  未署名 Response を拒否すること   │
├──────────────────────────────────────────────┤
│ Suite は署名のない Response を SP の ACS に   │
│ POST しました。                               │
│                                              │
│ 対象 SP の画面はどうなりましたか?             │
│  ○ エラーが表示され、ログインしなかった       │
│  ○ ログインが成功してしまった      ← FAIL     │
│  ○ その他 / 分からない            ← 判定保留  │
│                                              │
│ [送信]  [ブラウザの実際の画面を再確認する]    │
└──────────────────────────────────────────────┘
```

- 利用者の申告に基づく結果は、レポート上で必ず `(attested)` と表示する
- 申告に矛盾がある場合（例: Suite は HTTP 302 でセッション Cookie の発行を観測したのに
  「ログインしなかった」と申告）は `INCONSISTENT` として警告する
- 公開結果では attested な項目の件数を必ず表示する

## 4. 判定語彙（Verdict）

**適用されない**ことと**検証できなかった**ことを厳密に分ける。ここが最も間違えやすい。

| Verdict | 意味 | 母数 | MUST で起きたときの Run への影響 |
|---|---|---|---|
| `PASS` | 検証し、義務を満たしていた | 含む | — |
| `WARNING` | SHOULD / RECOMMENDED を満たさない、または PASS だが注意点がある | 含む | `CONFORMANT_WITH_WARNINGS` |
| `FAIL` | 検証し、義務を満たしていなかった | 含む | `NON_CONFORMANT` |
| `INCONSISTENT` | 利用者の申告と Suite の観測が矛盾する | 含む | `conformance = INDETERMINATE` / `completeness = INCOMPLETE`（申告のやり直しを促す） |
| `INDETERMINATE` | 実行したが判定に足る証拠が得られなかった | 含む | `conformance = INDETERMINATE` / `completeness = INCOMPLETE` |
| `NOT_VERIFIED` | **義務は適用されるが、この Run では検証できなかった**（`reason` 必須） | 含む | `conformance = INDETERMINATE` / `completeness = INCOMPLETE` |
| `NOT_OBSERVABLE` | 外部プロトコル面から**原理的に**検証できない。要件側の静的な属性 | 含む（別枠で表示） | 適合の主張から**明示的に除外**（§7） |
| `NOT_SUPPORTED` | `MAY` / `OPTIONAL` の義務を「未実装」と申告された | 除外 | — |
| `NOT_APPLICABLE` | **義務の適用条件が成立しない**（役割外、条件付き義務の条件が偽） | **除外** | — |
| `ERROR` | Suite 側の障害（タイムアウト、内部例外、Suite のバグ） | 含む | `conformance = INDETERMINATE` / `completeness = INCOMPLETE` |

### ★ `NOT_APPLICABLE` を使ってよい場合は 2 つだけ

**誤り**: 「Test Plan の構成上テストできないから `NOT_APPLICABLE`」。
実行環境を選んだことで、無条件の MUST が適用外になるわけではない。
これを許すと **MUST 要件の検証を構成で回避できてしまう**。

`NOT_APPLICABLE` が正しいのは次の 2 つのみ。

1. **役割が違う**: SP プロファイルにおける `IIP-IDP*`（そもそも義務の主体ではない）
2. **条件付き義務の条件が偽**: `IIP-SP14.b` は「SLO 対応を表明している SP」にのみ課される MUST。
   表明していなければこの義務は存在しない

それ以外の「実行できなかった」は全て **`NOT_VERIFIED`** であり、母数に含まれ、
MUST なら `conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる（[§7.2](#72-判定)）。

### `NOT_VERIFIED` の reason（必須）

| reason | 例 |
|---|---|
| `plan_configuration` | `suite_metadata_delivery: manual` を選んだため IIP-MD01〜04 を実行できない |
| `target_unreachable` | 対象から Suite に到達できず、バックチャネル系を実行できない（[07 §2](07-deployment-and-networking.md)） |
| `target_config_unavailable` | 製品に能力はあるが、**利用者の権限・環境の都合で**設定・確認できなかった |
| `capability_undetermined` | **製品にその能力があるのか、利用者が設定できないだけなのかを判別できなかった** |
| `precondition_failed` | 前提ケースが FAIL したため実行しなかった |
| `interaction_disallowed` | `allow_browser_steps: false` / `allow_attestation: false` |
| `user_skipped` | 利用者が実行しなかった |
| `timeout` | `WAITING_*` がタイムアウトした |
| `not_implemented` | Suite 側にまだ実装がない。**リリース時は 0 件であることを CI で強制する** |

> UI は `NOT_VERIFIED` を「未検証（あと N 件で完全なレポートになります）」として提示し、
> reason ごとに**どうすれば検証できるか**を出す。隠さない。

### ★ 「設定できない」ときの共通判定手順

多くの義務が「**〜できること**」という**能力**の要求である
（*MUST support the ability to …* / *MUST be configurable with …* / *MUST be capable of …*）。
対象側の設定が所望の状態にできなかったことを
**一律に `NOT_VERIFIED(target_config_unavailable)` にしてはならない**。

ただし **ケースが `FAIL` を返してもいけない**。
[05 §2.3](05-test-definition-format.md) の通り、ケースが返すのは `outcome` であり、
Verdict への変換は Evaluator が `obligation.level` を見て一元的に行う。
**ここを直接 `FAIL` にすると、SHOULD 義務を FAIL にする**（R2 で潰したはずの誤りが再発する）。

#### 手順

```
① まず適用性を評価する（§1）
     effective_result == FALSE  → NOT_APPLICABLE。ケースは実行しない
     ★ 例: IIP-ALG05.b（CBC 対応実装は使用時に警告すべき）は
        CBC 非対応なら条件が偽 → NOT_APPLICABLE であって FAIL ではない

② ケースを実行する。設定が達成できなければ利用者に問う（Runner が共通に出す）
   ┌──────────────────────────────────────────────┐
   │ Q. 製品にその設定能力が存在しますか?          │
   │   ○ 製品にその機能がない / 設定項目が存在しない│
   │   ○ 機能はあるが、私の権限・環境では変更できない│
   │   ○ 分からない                                │
   └──────────────────────────────────────────────┘

③ ケースは outcome を返す（Verdict は返さない）
```

| 回答 | ケースが返す `outcome` | `reason_code` |
|---|---|---|
| 製品にその機能がない | **`violated`** ※ | `capability_absent` |
| 権限・環境の制約で変更できない | `not_verified` | `target_config_unavailable` |
| 分からない | `not_verified` | `capability_undetermined` |

※ ただし `violated` を返すのは、**その設定能力自体が義務である**場合に限る（下記）。

#### `configuration_failure_semantics`（テスト定義の必須フィールド）

`mode: CONFIG` は**実行方式**であって、「設定能力が規範要件か」を意味しない。
テスト定義に明示する。

| 値 | 意味 | 能力なしのときの `outcome` |
|---|---|---|
| `normative_capability` | **設定できること自体が義務**（*MUST be configurable with at least two decryption keys* など） | `violated` + `capability_absent` |
| `test_precondition` | 設定は**テストを成立させるための前提**にすぎず、できないこと自体は義務違反ではない（例: 属性リリースポリシーを変えて差を観測する） | `not_verified` + `test_precondition_unavailable` |

#### Verdict への変換（Evaluator が行う）

```
outcome: violated (capability_absent)
   × MUST_CLASS   → FAIL
   × SHOULD_CLASS → WARNING          ★ FAIL にしない
   × MAY_CLASS    → NOT_SUPPORTED
```

#### 規約

1. **`mode: CONFIG` の全ケースが `configuration_failure_semantics` を持つ**（CI 規則 5d）
2. ケース実装は `FAIL` / `WARNING` を返さない。返せる型がない（[05 §4](05-test-definition-format.md)）
3. 質問文は Runner が共通に出す（文言のばらつきが判定のばらつきになる）
4. 「分からない」を選んだ Run は `completeness = INCOMPLETE`。後から再申告できる
5. 適用性の評価はケース実行より**先**。条件が偽の義務でこの手順に入ってはならない

> R9 でこの手順を新設したとき、**全 `CONFIG` ケースを直接 `FAIL` に写像**していた。
> ケースが Verdict を返さないという設計に反し、SHOULD 義務と条件付き義務を誤判定する。
> 判定規則を共通化しても、**変換は必ず Evaluator に通す**。

### `NOT_SUPPORTED` の適用範囲（再掲・§3 の規則）

```
義務の RFC2119 レベル        利用者が「未実装」と申告した場合
─────────────────────────────────────────────────
MUST / MUST NOT / REQUIRED  →  FAIL   (reason: declared-unsupported)
SHOULD / RECOMMENDED        →  WARNING
MAY / OPTIONAL              →  NOT_SUPPORTED
```

### `INCONSISTENT`

利用者の申告が Suite の観測と矛盾する場合に付く。
例: Suite は対象 ACS への POST 後に `302 → 保護リソース` と `Set-Cookie` を観測したのに、
利用者が「ログインしなかった」と申告した。

- 自動的に PASS にも FAIL にもしない
- UI で矛盾点（観測した証拠）を提示し、**再申告または再実行**を促す
- 未解消のまま Run を終えた場合、MUST なら `conformance = INDETERMINATE` / `completeness = INCOMPLETE`

## 5. Negative test の証拠ラダー

「対象が不正なメッセージを拒否したこと」をどう認定するか。強い順に。

| 段階 | 証拠 | 自動判定 |
|---|---|---|
| L1 | SAML の `<Status>` にエラー（`Requester` / `RequestDenied` 等）を含む応答が返った | 可 |
| L2 | 対象エンドポイントが HTTP 4xx / 5xx を返した | 可 |
| L3 | Suite が観測できる範囲で、成功を示す状態遷移が起きなかった | 条件付きで可 |
| L4 | 対象の画面にエラーが表示されたことを利用者が申告した | `ATTESTED` |
| — | 上記いずれも得られない | `INDETERMINATE` |

**ルール: 「何も起きなかった」ことだけを根拠に PASS にしない。**

L3 の自動判定は Suite 側の観測点に限る（Suite の ACS に成功 Response が届かなかった等）。
対象のセッション Cookie は見られないため、それ以外は L4 に落とす。

> IIP-IDP05（条件が許すときエラー Response を返すこと）が満たされている IdP ほど、
> negative test を L1 で自動判定できる。IIP-IDP05 は他の多くのテストの検出力を左右する
> **キー要件**として、実行順序の早い位置に置く。

## 6. 集約規則（決定表）

### 6.1 重大度順序

集約は常に **「最も重大なものが勝つ」**。順序を一意に定める。

```
FAIL  >  INCONSISTENT  >  ERROR  >  INDETERMINATE  >  NOT_VERIFIED
      >  WARNING  >  PASS  >  NOT_SUPPORTED  >  NOT_OBSERVABLE  >  NOT_APPLICABLE
```

設計上の判断:

- **`FAIL` > `ERROR`**: 既知の不適合が Suite 側の障害に隠れてはならない。
  1 ケースが FAIL、別のケースが ERROR なら、義務は `FAIL`
- **`NOT_VERIFIED` > `PASS`**: 一部のケースだけ通っても、未検証のケースが残る義務は
  `NOT_VERIFIED`。**これがレビュー指摘 2 の修正点**
- **`NOT_VERIFIED` > `WARNING`**: 未検証は SHOULD 違反より不完全性が高い
- **`INCONSISTENT` > `ERROR`**: 矛盾は利用者の行動で解消できるため、先に提示する

### 6.2 CaseRun → Obligation

義務の Verdict は **ケースの集約だけでは決まらない**。
適用性の評価結果（[§1 の三値評価](#-条件の評価は三値で行う自己申告だけを信じない)）も入力になる。

```
verdict(obligation) = max_severity(
      applicabilityVerdict(obligation)              ★ 適用性の評価も 1 つの入力
    ∪ { verdict(case) for case in cases(obligation) }
)

applicabilityVerdicts(o) = 次の 2 つの入力（独立）
    ① effective_result(o) == FALSE    → NOT_APPLICABLE
       effective_result(o) == UNKNOWN → NOT_VERIFIED(applicability_undetermined)
       effective_result(o) == TRUE    → （入力なし。ケースを実行して集約）
    ② conflict(o) == true             → INCONSISTENT   ★ ① と独立に注入される
    （`CONFLICT` という第 4 の値は存在しない。conflict は独立した boolean）
```

`INCONSISTENT` は重大度順序で `PASS` と `NOT_APPLICABLE` の両方より上位なので、

- 矛盾したまま全ケースが PASS しても義務は `INCONSISTENT` になる
- **矛盾したまま義務が `NOT_APPLICABLE` として黙って除外されることもない**

矛盾を無視して適合を主張する経路がなくなる。

`ApplicabilityEvaluation` は `Evaluator.evaluate()` の明示的な入力である
（署名の正本は [§7.5](#75--判定は-1-つの関数に閉じ込める)）。

評価結果は全件（`effective_result` ∈ TRUE/FALSE/UNKNOWN と `conflict`）を結果 JSON の
`applicability[]` に、判断根拠つきで記録する（[06 §1](06-results-and-publication.md)）。

```
verdict_from_cases(obligation) = max_severity( verdict(case) for case in cases(obligation) )

ただし:
  cases(obligation) が空          → NOT_OBSERVABLE または NOT_VERIFIED(not_implemented)
                                    （coverage.yaml の testability により決まる。CI で検証）
  obligation.condition が偽       → NOT_APPLICABLE（ケースを実行しない）
  obligation.roles に対象役割なし → NOT_APPLICABLE
```

### 6.3 Obligation → Requirement（役割別）

```
verdict(requirement, role) = max_severity(
    verdict(o) for o in obligations(requirement) if role in o.roles
)
すべて NOT_APPLICABLE → NOT_APPLICABLE
```

要件表示は**役割別**に行う。IdP プロファイルの Run では IdP に適用される義務のみを出す。

### 6.4 決定表（テーブル駆動テストで固定する）

実装では次の表をそのままテストデータにする。`>` は左が勝つ。

| 入力（同一義務内のケース集合） | 集約結果 |
|---|---|
| `{PASS}` | `PASS` |
| `{PASS, PASS}` | `PASS` |
| `{PASS, WARNING}` | `WARNING` |
| `{PASS, NOT_VERIFIED}` | **`NOT_VERIFIED`** ← 旧規則では PASS だった |
| `{PASS, NOT_VERIFIED, WARNING}` | `NOT_VERIFIED` |
| `{PASS, FAIL}` | `FAIL` |
| `{FAIL, ERROR}` | **`FAIL`** ← 既知の FAIL を隠さない |
| `{FAIL, INCONSISTENT}` | `FAIL` |
| `{INCONSISTENT, ERROR}` | `INCONSISTENT` |
| `{ERROR, INDETERMINATE}` | `ERROR` |
| `{INDETERMINATE, NOT_VERIFIED}` | `INDETERMINATE` |
| `{NOT_VERIFIED, NOT_APPLICABLE}` | `NOT_VERIFIED` |
| `{PASS, NOT_APPLICABLE}` | `PASS` |
| `{NOT_SUPPORTED, NOT_APPLICABLE}` | `NOT_SUPPORTED` |
| `{NOT_OBSERVABLE}` | `NOT_OBSERVABLE` |
| `{NOT_OBSERVABLE, PASS}` | `PASS`（一部でも検証できたなら検証結果を優先） |
| `{NOT_OBSERVABLE, FAIL}` | `FAIL` |
| `{NOT_APPLICABLE}` | `NOT_APPLICABLE` |
| `{}`（ケースなし） | `NOT_OBSERVABLE` または `NOT_VERIFIED(not_implemented)` |

> CI に `VerdictAggregationTest` を置き、**10 値 × 10 値の全 100 組み合わせ**と
> 上表を突き合わせる。順序を変える PR は必ずこのテストを壊す。

## 7. Run の全体判定

### 7.1 母数の定義

```
applicable   = 適用される全義務（NOT_APPLICABLE を除いた全て）
must_set     = applicable のうち level ∈ {MUST, MUST NOT, REQUIRED}
observable   = must_set のうち NOT_OBSERVABLE でないもの   ← 適合主張の母数
```

### 7.2 判定

`WARNING` は「義務は満たしているが注意点がある」または「SHOULD/RECOMMENDED を満たさない」を表す。
**MUST 義務に `WARNING` が付くこともある**ため、MUST の充足判定は `{PASS, WARNING}` を
「充足」として扱う。

```
satisfied(o)   ≡ verdict(o) ∈ {PASS, WARNING}
unresolved(o)  ≡ verdict(o) ∈ {NOT_VERIFIED, INDETERMINATE, INCONSISTENT, ERROR}
```

**適合性（conformance）と実行完全性（completeness）は別の軸である。**
1 つのラベルに畳むと、MUST が全部通っていれば SHOULD が全滅していても
`CONFORMANT` になってしまう（レビュー指摘 7）。**2 つのフィールドに分ける。**

```
run.conformance ∈ { CONFORMANT, CONFORMANT_WITH_WARNINGS,
                    CONFORMANT_WITH_DECLARED_EXCLUSIONS, NON_CONFORMANT, INDETERMINATE }

  NON_CONFORMANT             ∃ o ∈ applicable : verdict(o) = FAIL
  INDETERMINATE              ¬NON_CONFORMANT ∧ ∃ o ∈ must_observable : unresolved(o)
  CONFORMANT_WITH_DECLARED_EXCLUSIONS
                             ∀ o ∈ must_observable : satisfied(o)
                             ∧ coverage.excluded_by_declaration > 0
                             （WARNING の有無を問わない。★ 最優先で判定する）
  CONFORMANT_WITH_WARNINGS   ∀ o ∈ must_observable : satisfied(o)
                             ∧ coverage.excluded_by_declaration = 0
                             ∧ ∃ o ∈ W : verdict(o) = WARNING
  CONFORMANT                 ∀ o ∈ must_observable : verdict(o) = PASS
                             ∧ coverage.excluded_by_declaration = 0
                             ∧ ¬∃ o ∈ W : verdict(o) = WARNING

  W = applicable ∩ selected_profile      ← ★ WARNING を数える集合
      （選択したプロファイル(Core / Full)に含まれる、適用される全義務。レベルを問わない）

run.completeness ∈ { COMPLETE, INCOMPLETE }

  INCOMPLETE   ∃ o ∈ observable(全レベル。選択したプロファイルに含まれる全義務)
               : unresolved(o)
  COMPLETE     それ以外
```

- `run.conformance` の**合否**は `must_observable` のみで決まる。適合の主張はここに閉じる
- ★ ただし **`WARNING` を数える集合 `W` は選択プロファイルの全義務**である。
  SHOULD 違反があれば `CONFORMANT` ではなく `CONFORMANT_WITH_WARNINGS` になる。
  「MUST が全部通っているので警告は表に出ない」という状態を作らない。
  `W` の定義を `must_observable` にすると SHOULD 違反が完全に隠れ、
  `applicable` 全体にすると Core 実行時に Full の義務まで数えてしまうため、
  **選択プロファイルとの積**を取る
- `VerdictAggregationTest` に「MUST 全 PASS + SHOULD 1 件 WARNING」と
  「MUST 1 件 satisfied_with_note」の両ケースを含める
- `run.completeness` は **選択したプロファイル（Core / Full）の全義務**を見る。
  Full を選んだのに SHOULD 義務が `ERROR` や `NOT_VERIFIED` なら `INCOMPLETE` になる
- **UI とレポートは 2 つを必ず併記する**。片方だけの表示を禁止する

```
CONFORMANT (tested scope)  ·  INCOMPLETE (3 obligations unresolved)
```

- 従来の単一ラベル `INCOMPLETE` は `conformance = INDETERMINATE` に相当する。
  混同を避けるため、適合性側の名称を `INDETERMINATE` に改めた

`RunVerdictTest` で両フィールドの網羅性・排他性を検証する
（[05 §5](05-test-definition-format.md) の規則 18）。

### 7.3 ★ `NOT_OBSERVABLE` な MUST 義務があるときの適合表明

Kantara IIP には、外部のプロトコル面からは原理的に検証できない MUST 義務がある
（例: IIP-SP12「persistent NameID に仕様を超えた意味を持たせない」）。
これらを理由に全ての Run を `conformance = INDETERMINATE` にすると、最上位の判定が無意味になる。

そこで **適合の主張範囲を構造的に限定する**。

- 適合判定の母数は `observable` のみ
- `NOT_OBSERVABLE` な MUST 義務は**必ず件数と一覧を併記**する
- UI・レポート・公開ページは **`CONFORMANT` を単独で表示してはならない**。
  常に結果 JSON の `conformance_statement` をそのまま伴う

> ⚠ **ここに置いていた表示例は削除しました。**
> レビューで、SP の Run の例に `IIP-IDP02.b` が混在し、
> `IIP-SP11.b` が現在の義務分解に存在しないことが指摘されました（役割・キーの不整合）。
> **`conformance_statement` も `Evaluator` の golden fixture から生成**します
> （[§7.5](#75--判定は-1-つの関数に閉じ込める)）。生成に切り替わるまで例は置きません。

`conformance ≠ CONFORMANT` の場合も同様に、未解決の義務 ID と reason を全件列挙する。

### 7.4 カバレッジ指標（結果 JSON に必須）

適合ラベルだけでは「何をどれだけ確かめたか」が伝わらない。
**分母と分子を一意に定義する**（レビュー指摘 14）。

```
applicable       = 適用される全義務（NOT_APPLICABLE を除く）
must_applicable  = applicable のうち level ∈ MUST_CLASS
                   MUST_CLASS = {MUST, MUST_NOT, REQUIRED}
must_observable  = must_applicable のうち verdict ≠ NOT_OBSERVABLE   ← 適合主張の母数
must_resolved    = must_observable のうち verdict ∈ {PASS, WARNING, FAIL}
                   （＝結論に達したもの。NOT_VERIFIED / INDETERMINATE /
                     INCONSISTENT / ERROR は含まない）

verified_ratio   = must_resolved / must_observable     ← 分母は must_observable
```

`verified_ratio` は**「適合率」ではなく「結論に達した割合」**である。
FAIL も分子に入る。名前が誤解を招くため、UI では
`Resolved: 45 / 47 externally-testable MUST obligations` のように分数で表示し、
比率単独では出さない。

```json
"coverage": {
  "obligations_total": 63,
  "obligations_applicable": 61,
  "must_applicable": 48,
  "must_observable": 45,
  "must_resolved": 45,
  "must_unresolved": 0,
  "must_not_observable": 3,
  "verified_ratio": 1.0,
  "attested_obligations": 11,
  "applicability_from_declaration_only": 2
}
```

**公開ページはこの指標を適合ラベルと同じ大きさで表示する。**
「PASS 74」だけが独り歩きする状態を構造的に防ぐ。

### 7.5 ★ 判定は 1 つの関数に閉じ込める

Run 判定・要件集約・カバレッジ指標を導出するコードは **1 箇所**に置く。

```java
public final class Evaluator {
    /** ★ これが唯一の正本。§6.2 と同じ署名でなければならない。 */
    public static RunResult evaluate(CoverageCatalog catalog,
                                     TestPlan plan,
                                     List<ApplicabilityEvaluation> applicability,
                                     List<CaseRun> caseRuns,
                                     List<SuiteIncident> incidents);
}
```

> **署名は 1 箇所にしか書かない。** 前回のレビューで、§6.2 で
> `ApplicabilityEvaluation` を入力に加えたのに、ここの署名が旧形式のまま残っており、
> 実装者がこちらを正とすると同じ問題が再発する状態になっていた。
> ドキュメント側では **§7.5 を正本**とし、§6.2 は参照だけにする。

- UI・`result.json`・`report.html`・ドキュメントの例、**全てがこの関数の出力を使う**
- ドキュメント中の JSON 例は手書きしない。`Evaluator` の出力を
  golden fixture として `docs/` に生成し、CI で差分を検出する
  （[06 §1](06-results-and-publication.md)、[05 §5](05-test-definition-format.md) の規則 23）

> 前回のレビューで、ドキュメント中の `result.json` 例が
> 自分自身の判定規則と矛盾していることが指摘された。
> **例を手で書く限り必ずずれる。** 生成物にする。

## 8. Test Run の状態機械

```
        ┌─────────┐
        │ CREATED │
        └────┬────┘
             │ start
             ▼
      ┌─────────────┐   全ケース終了    ┌───────────┐
      │  RUNNING    │──────────────────▶│ COMPLETED │
      └──┬───┬───┬──┘                   └───────────┘
         │   │   │                            ▲
         │   │   └── 利用者の操作待ち ────┐    │
         │   │       ┌────────────────┐  │    │
         │   └──────▶│ WAITING_BROWSER│──┘    │
         │           └────────────────┘       │
         │           ┌────────────────┐       │
         ├──────────▶│ WAITING_ATTEST │───────┤
         │           └────────────────┘       │
         │           ┌────────────────┐       │
         ├──────────▶│ WAITING_CONFIG │───────┤
         │           └────────────────┘       │
         │           ┌────────────────────┐   │
         └──────────▶│ WAITING_CREDENTIAL │───┘
                     └────────────────────┘
                       再起動で ECP 資格情報が失われた場合
                       （[05 §4.3.2](05-test-definition-format.md)）
             │ abort / timeout
             ▼
        ┌─────────┐
        │ ABORTED │
        └─────────┘
```

- `WAITING_*` には**タイムアウト**を設ける（既定 15 分）。
  切れたケースは **`NOT_VERIFIED(timeout)`**（`SKIPPED` は語彙から廃止済み）
- Run 中の状態は SSE で UI にプッシュする
- Run を中断しても Transcript は残す

## 9. テスト実行順序

順序に依存がある。Runner は依存を宣言的に扱う。

```
1. Preflight        Suite 自身の到達性、時刻、TLS、対象メタデータ取得可否
2. Metadata (静的)  対象メタデータの構文・署名・アルゴリズム宣言の検査（AUTOMATED）
3. Metadata (動的)  Suite メタデータの配布・署名・validUntil（CONFIG）
4. Algorithms       署名・暗号アルゴリズムの対応確認
5. Core SSO         Web Browser SSO の正常系（BROWSER・ここで初回ログイン）
6. SSO variations   NameID / AuthnContext / ForceAuthn / IsPassive / 属性
7. Error handling   IIP-IDP05 を含む negative 系
8. SLO              Single Logout（セッションを壊すので後半）
9. ECP              バックチャネル（AUTOMATED、いつでも可）
```

`ForceAuthn` と `SLO` はセッションを壊すため必ず末尾に置く。
Runner はケース定義の `requires_session` / `destroys_session` フラグから順序を決める。

## 10. Preflight チェック（新規追加）

元メモにはないが、これがないと利用者が原因不明の失敗に悩まされる。
Test Plan 作成直後に必ず走る。

- [ ] `PUBLIC_BASE_URL` が設定されており、Suite 自身がその URL で自分に到達できるか
- [ ] コンテナの時刻が NTP 同期されているか（ズレは全テストを壊す）
- [ ] 対象のメタデータ URL が取得でき、パースでき、有効期限内か
- [ ] 対象メタデータの `SingleSignOnService` / `AssertionConsumerService` が
      Suite から到達可能なホストか（バックチャネルが必要な場合）
- [ ] **`Target → Suite` の到達性は Preflight では確定しない**。Preflight は
      `reachability = ASSERTED` までしか出せず、`CONFIRMED` への昇格は
      対象からの inbound リクエストを観測して初めて起きる（[07 §2](07-deployment-and-networking.md)）
- [ ] Suite の base URL が HTTPS か（多くの SP は http の ACS を拒否する）
- [ ] 対象の TLS 証明書チェーンが検証可能か（自己署名なら明示的に許可させる）

Preflight の結果は Run の一部として記録し、失敗した項目に依存するテストは
**`NOT_VERIFIED`（該当する reason 付き）**にする。`NOT_APPLICABLE` にはしない（§4）。
