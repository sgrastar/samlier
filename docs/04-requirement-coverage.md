# 04. 要件カバレッジマップ（生成物）

> ⚠ **`tests/coverage.yaml` からの生成物です。手で編集しないでください。**
> 再生成: `python3 tools/g1_docgen.py`（ネットワーク不要 / authoring 入力不要）
> G1 状態: **PENDING_REVIEW**

対象文書: **SAML V2.0 Implementation Profile for Federation Interoperability, Version 1.1 (2019-12-18)**  
https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html  
原文ダイジェスト: `sha256:6cbc97a652651d6a5cff26a41c51195b8b914ed59dc63ed1d6ce254e88edd13d`

検証: `python3 tools/g1_validate.py` → `build/spec-reconcile-report.json`

## サマリ

| 指標 | 値 |
|---|---|
| 要件 | 69 |
| 義務（obligation） | 147 |
| うち MUST_CLASS | 125 |
| うち SHOULD_CLASS | 11 |
| うち MAY_CLASS | 11 |
| 条件付き義務 | 19 |
| IdP プロファイル | 113 義務（Core 84 / Full 29） |
| SP プロファイル | 102 義務（Core 77 / Full 25） |
| 非規範（イタリック）スパン | 26 |

**Testability**

| 記号 | 意味 | 件数 |
|---|---|---|
| `AUTOMATED` | Suite と対象の直接通信で完結（ブラウザ不要） | 8 |
| `BROWSER` | 利用者のブラウザが必要 | 61 |
| `ATTESTED` | 対象内部の挙動を利用者が申告 | 15 |
| `CONFIG` | 対象側の設定変更を依頼したうえで実行 | 61 |
| `NOT_OBSERVABLE` | 外部から原理的に検証不能。ケースを作らない | 2 |

**判定に関する注意**

- 判定レベルの唯一の出典は `tests/coverage.yaml` です
- ケースは `outcome` を返し、Verdict への変換は Evaluator が `level` を見て行います（[03 §4](03-test-model.md)）
- `NOT_APPLICABLE` は「役割違い」と「条件付き義務の条件が偽」のみ。実行できなかったものは `NOT_VERIFIED` です
- **Core / Full は Samlier 独自の分類**であり、IIP 原文にこの区別はありません

## 要件と義務

### 2.1 Common / General

#### IIP-G01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-G01) ／ 節ダイジェスト `sha256:53941f0bef83…` ／ 節長 781 ／ 非規範スパン 3

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-G01.a` | MUST | idp/sp | `BROWSER` | — | core | xsd:dateTime の解釈とそれに基づくポリシー適用で合理的なクロックスキューを許容する |

<details><summary><code>IIP-G01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-e627eb2f62` 対象が申告した許容幅 T の内側（T-δ）に IssueInstant をずらす → 受理されるべき（唯一の verdict 対象）
  - `v-5b56a30a71` NotBefore / NotOnOrAfter を T-δ ずらす → 受理されるべき
  - `v-b1b5108e0e` メタデータの validUntil を T-δ ずらす → 受理されるべき
  - `v-332d560ed7` 情報記録のみ: T の外側（T+δ）にずらしたときの挙動。受理しても違反ではない
- **対照（negative control）**:
  - ★ Samlier は絶対閾値を持たない。対象が申告した許容幅 T の内側が受理されることだけを判定する
  - ★ 訂正: 前版は T+δ の受理を violated にしていたが、原文は『合理的なスキューを許容する』義務であって『それ以上を拒否する』義務ではない。広い許容幅を持つ適合実装を FAIL にしてしまう。境界外は advisory clock_skew.very_permissive として記録し判定に使わない
  - T を申告できない場合は NOT_VERIFIED。設定可能性は原文の義務ではないので違反にしない
- **設定不能時の意味**: `test_precondition`
- **注記**: 原文には普遍的な秒数の受理義務がない（『3–5 分』はイタリック＝非規範）。前版は ±180 秒の受理を必須 variant にしており、許容幅を 120 秒に設定した適合実装を違反にしうる誤りだった。T を申告・設定できない場合は NOT_VERIFIED。過大許容は advisory clock_skew.very_permissive として記録し判定に使わない。
- **source_clauses**: `[0, 155)` `sha256:9a9e31b61f2f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-G02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-G02) ／ 節ダイジェスト `sha256:1ca7eb8542d7…` ／ 節長 566 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-G02.a` | MUST | idp/sp | `BROWSER` | — | core | 有効な XML 文字の任意の組合せからなる 256 文字までの xs:string 値をエラーなく受理する |
| `IIP-G02.b` | MUST | sp | `CONFIG` | — | core | SP は受信した 256 文字までの xs:string 値を切り詰めない |
| `IIP-G02.c` | MUST | idp | `ATTESTED` | — | core | IdP は受信した 256 文字までの xs:string 値を切り詰めない |

<details><summary><code>IIP-G02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-c5fdf4c301` 【標準型】transient NameID に 256 文字（SAML2Core 8.3.8 の上限と一致）
  - `v-07274cb7c4` 【標準型】persistent NameID に 256 文字（SAML2Core 8.3.7 の上限と一致）
  - `v-fe0a924d7a` 【標準型】AuthnRequest/@ProviderName に 256 文字
  - `v-47b175417d` 【利用者定義型】<saml:AttributeValue xsi:type="myns:MyStringType"> のように、利用者定義スキーマの xs:string 派生型の値に 256 文字
  - `v-090387ce26` 【利用者定義型】<samlp:Extensions> に置いた利用者定義要素の xs:string 型属性に 256 文字
  - `v-8a37fd3044` 【利用者定義型】<saml:Advice> に置いた利用者定義要素の xs:string 型要素内容に 256 文字
  - `v-111b055549` 文字種: len=255 境界
  - `v-e69ea25841` 文字種: len=256 境界
  - `v-b3e41d759d` 文字種: 非 ASCII（CJK / キリル）
  - `v-eb3d87c044` 文字種: 結合文字（正規化で長さが変わる）
  - `v-5e3fe3ff57` 文字種: 補助平面のコードポイント（孤立サロゲートは生成しない）
  - `v-3d0ed2363d` 文字種: TAB / LF を文字参照で（リテラルは XML 属性値正規化で空白になる）
  - `v-16ec37dcf7` 文字種: XML 構文上特別な文字（< & " ' >）を文字参照・エンティティ参照で
- **対照（negative control）**:
  - ★ 【型種別】×【文字種】の両軸を試す。標準型だけ通しても『user-defined types にも適用される』ことを検証していない
  - ★ 本義務が判定するのは『エラーにならないこと』だけ。切り詰めの有無は IIP-G02.b（SP）/ IIP-G02.c（IdP）で判定する
  - ★ 対照: 255 文字は通り 256 文字で拒否される実装を検出できること（境界を跨ぐ 2 ケースが必須）
  - リテラル TAB/LF は XML 属性値正規化で空白になる。リテラル版と文字参照版を別ケースにし、比較は XML 解析後の値で行う
  - 長さは Unicode コードポイント数で数える
- **適用範囲**: 冒頭に『SAML 標準やプロファイル文書に固有の制約がない場合』という限定がある。SAML が長さ・文字種を制約していないフィールドを選ぶこと。
- **注記**: 受理の証拠はトランスクリプトで足りる（エラー応答がなく、フローが完了する）。ただし <samlp:Extensions> / <saml:Advice> に置いた利用者定義型は『無視してよい』ため、成功応答は『受理した』と『無視した』を区別しない。無視されていないこと・切り詰められていないことは IIP-G02.b / .c で別途観測する。型が xs:string と確定するフィールドを使うこと（saml:Attribute/@Name・@FriendlyName は SAML スキーマ定義済みの型なので user-defined type の対照にはならない）。
- **source_clauses**: `[81, 291)` `sha256:d07e84e7979b…` , `[293, 435)` `sha256:fe98afa5ffc2…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-G02.b</code> の詳細</summary>

- **必要な variant**:
  - `v-08c453934f` 受信した NameID（transient / persistent）を読み戻し、256 コードポイントのまま保持されている
  - `v-bb65fb78d9` 受信した <saml:AttributeValue>（標準型）を読み戻し、256 コードポイントのまま保持されている
  - `v-9a4cea85bb` 受信した利用者定義型の AttributeValue（xsi:type）を読み戻し、値が保持されている
  - `v-c4c199e46b` 文字種: 非 ASCII / 結合文字 / 補助平面のコードポイントが失われていない
  - `v-e12a741841` 文字種: 文字参照で送った TAB / LF が XML 解析後の値として保持されている
  - `v-814d4ab87d` 対照: 255 文字は完全一致、256 文字だけ末尾が欠ける実装を検出できる
- **対照（negative control）**:
  - ★ 成功応答は『切り詰めなし』の証拠にならない。<samlp:Extensions> / <saml:Advice> の未知内容は無視してよいので、『無視した』『切り詰めた』『保持した』の 3 つを成功応答だけでは区別できない
  - ★ 読み戻しは対象側の観測面から行う。Suite が送った値と、対象が読み戻した値を Unicode コードポイント列として比較する
  - ★ 読み戻し経路が用意できない場合は not_verified(no_readback_path)。対象の不適合ではない
- **設定不能時の意味**: `test_precondition`
- **適用範囲**: 適用範囲は IIP-G02.a と同じ。本義務は『切り詰めなし』の側だけを扱い、値の読み戻し経路を必要とする。
- **注記**: 読み戻し経路の例: SP の属性表示エンドポイント（Shibboleth の Session ハンドラ等）、対象アプリに置いた診断用ページ、対象が発行するセッション情報。いずれも Test Plan の preflight で URL と読み取り方を登録し、値の突き合わせは Suite が自動で行う。経路がない場合は IIP-G02.c と同じく申告に落とすのではなく not_verified を返す（SP は読み戻し面を持つのが通例で、申告に落とすと検出力を失うため）。
- **source_clauses**: `[81, 291)` `sha256:d07e84e7979b…` , `[293, 435)` `sha256:fe98afa5ffc2…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-G02.c</code> の詳細</summary>

- **必要な variant**:
  - `v-b69b5e2e3f` 往復経路がある場合: <samlp:ManageNameIDRequest>/<samlp:NewID>（schema 上 type="string"）に 256 文字を設定し、以降の Assertion の SPProvidedID が同一の 256 コードポイントで返る（SAML2Core 3.6 対応時のみ自動照合できる）
  - `v-c7e5452835` 往復経路がない場合: AuthnRequest/@ProviderName・NameIDPolicy/@SPNameQualifier・<samlp:Extensions> に送った 256 文字が、対象の管理画面・監査ログ・セッション情報で切り詰められていないことを申告で確認する
  - `v-cf2254ee2d` 文字種: 非 ASCII / 結合文字 / 補助平面のコードポイントが失われていないこと（同上の経路で確認）
- **対照（negative control）**:
  - ★ 往復経路のある variant（SPProvidedID）を優先する。申告のみの結果と自動照合の結果は証拠ラダー上の等級が異なる
  - ★ SPProvidedID による往復は IIP-SSO05.a5 と同じ観測を使うが、判定対象は別（あちらは値の出所、こちらは長さの保存）
  - ★ 申告できない場合は not_verified(attestation_unavailable)。対象の不適合ではない
- **適用範囲**: 適用範囲は IIP-G02.a と同じ。IdP は受理した xs:string 値をプロトコル面に再出力しないものが多く、観測は原則として申告になる。
- **注記**: IIP-G02.a（受理）と本義務（非切り詰め）を分けたのは、成功応答が『無視した』と区別できないため。IdP 側は読み戻し面が標準化されておらず、SAML2Core 3.6 の Name Identifier Management に対応する実装だけが自動照合できる。
- **source_clauses**: `[81, 291)` `sha256:d07e84e7979b…` , `[293, 435)` `sha256:fe98afa5ffc2…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-G03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-G03) ／ 節ダイジェスト `sha256:b4fcb67b6c41…` ／ 節長 133 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-G03.a` | MUST_NOT | idp/sp | `AUTOMATED` | — | core | DTD を含む SAML プロトコルメッセージを送信しない |
| `IIP-G03.b` | MUST | idp/sp | `BROWSER` | — | core | DTD を含む SAML プロトコルメッセージを拒否できる |

<details><summary><code>IIP-G03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-b0fbefde6c` 対象が生成した全 SAML プロトコルメッセージを Transcript 全件で検査
- **対照（negative control）**:
  - 受動的な常時チェック。全ケースに横断適用する
- **source_clauses**: `[0, 29)` `sha256:0c97ff7a8417…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-G03.b</code> の詳細</summary>

- **必要な variant**:
  - `v-9b68bf247b` DOCTYPE 付き AuthnRequest
  - `v-d96f020574` DOCTYPE 付き Response
  - `v-56b1a00942` DOCTYPE + 外部エンティティ参照
- **対照（negative control）**:
  - 拒否の証拠は 03 の証拠ラダーに従う。無反応を PASS にしない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[34, 133)` `sha256:c96995d7bd60…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 2.2 Common / Metadata and Trust Management

#### IIP-MD01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD01) ／ 節ダイジェスト `sha256:a62aa94bedb6…` ／ 節長 398 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD01.a` | MUST | idp | `CONFIG` | — | core | md:EntityDescriptor をルートとするメタデータを MDQ で取得できる |
| `IIP-MD01.b` | SHOULD | sp | `CONFIG` | — | full | (SP) md:EntityDescriptor をルートとするメタデータを MDQ で取得できることが望ましい |
| `IIP-MD01.c` | MUST | idp/sp | `CONFIG` | `claims_mdq_support`<br>(CLAIM_BASED) | core | MDQ 対応を表明する実装は、SAML メッセージを受け取った任意のピアについて 1 つ以上の MDQ responder から取得・利用できる |

<details><summary><code>IIP-MD01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-87e4be2b8e` 対象を Suite の /mdq/{entityID} に向ける
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 166)` `sha256:137fea64b4d3…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD01.b</code> の詳細</summary>

- **必要な variant**:
  - `v-01c4e55224` 同上
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 166)` `sha256:137fea64b4d3…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD01.c</code> の詳細</summary>

- **必要な variant**:
  - `v-512b5f8fcb` 事前登録していない第 2 の entityID(secondary_peer)からメッセージを送り、MDQ で動的取得できるか
- **対照（negative control）**:
  - 『任意のピア』が要点。事前登録済みの entityID だけで PASS にしない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[201, 398)` `sha256:1a71b947a778…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD02) ／ 節ダイジェスト `sha256:03a520f61183…` ／ 節長 899 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD02.a` | MUST | idp/sp | `CONFIG` | — | core | HTTP/1.1 で定期的にメタデータを取得し、検証成功後に自動反映する |
| `IIP-MD02.b` | MUST | idp/sp | `CONFIG` | — | core | HTTP/1.1 の 301・302・307 リダイレクトに追従する |
| `IIP-MD02.c` | MUST | idp/sp | `CONFIG` | — | core | この機構で md:EntityDescriptor と md:EntitiesDescriptor の両方をルートとするメタデータを消費できる |
| `IIP-MD02.d` | MUST | idp/sp | `CONFIG` | — | core | EntitiesDescriptor がルートの場合、子要素が任意個数でも許容する |

<details><summary><code>IIP-MD02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-97c3cf9c26` Suite 側メタデータを変更し metadata_refresh_wait_seconds 経過後に反映を確認
- **対照（negative control）**:
  - ETag / Last-Modified は原文にないので判定に使わない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 215)` `sha256:cead52521318…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD02.b</code> の詳細</summary>

- **必要な variant**:
  - `v-f59c1e5d2a` 301 を返す metadata URL
  - `v-ad139f6037` 302 を返す metadata URL
  - `v-d97fc1a4de` 307 を返す metadata URL
- **対照（negative control）**:
  - 3 つのステータスコードを個別 variant にする。1 つで PASS にしない
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[216, 284)` `sha256:1d899d31d098…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD02.c</code> の詳細</summary>

- **必要な variant**:
  - `v-f1b5914003` EntityDescriptor をルートとする variant
  - `v-ffde7d6bc0` EntitiesDescriptor をルートとする variant
- **対照（negative control）**:
  - 両方を個別 variant にする
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[285, 439)` `sha256:e846fa6f6c57…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD02.d</code> の詳細</summary>

- **必要な variant**:
  - `v-b8bdb7fc14` 子 1 個
  - `v-6e369e76cd` 子 2 個
  - `v-48d8016661` 子 50 個
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[440, 505)` `sha256:6ad7c544c3e5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD03) ／ 節ダイジェスト `sha256:b103b4db3a97…` ／ 節長 541 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD03.a` | MUST | idp/sp | `CONFIG` | — | core | ルート要素に付与されたエンベロープ XML 署名を検証してメタデータの真正性・完全性を検証できる |
| `IIP-MD03.b` | MUST | idp/sp | `CONFIG` | — | core | メタデータ署名検証に使う公開鍵は帯域外で設定される |
| `IIP-MD03.c` | MUST | idp/sp | `CONFIG` | — | core | 証明書のその他の内容を無視し、公開鍵のみで XML 署名を検証できる |
| `IIP-MD03.d` | MUST | idp/sp | `CONFIG` | — | core | 信頼鍵の利用を単一のメタデータソースに限定できる |
| `IIP-MD03.e` | MAY | idp/sp | `CONFIG` | — | full | メタデータ署名検証に使う公開鍵は X.509 証明書に格納されていてもよい |

<details><summary><code>IIP-MD03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-ca7aad37fa` variant=unsigned
  - `v-ae722a3ed5` variant=badsig
  - `v-85d90c9bab` variant=signed-with-other-key
  - `v-8f2580aad3` 正常署名（対照）
- **対照（negative control）**:
  - 正常署名が受理されることを対照に置く。全部拒否する実装を PASS にしない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 183)` `sha256:3dac3bd94487…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD03.b</code> の詳細</summary>

- **必要な variant**:
  - `v-2cc9590bfd` メタデータ内の証明書とは異なる鍵で署名した variant を配布し、帯域外設定鍵で検証されるか
- **対照（negative control）**:
  - メタデータ自身に含まれる鍵で検証している実装は .b 違反
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[184, 275)` `sha256:92864e274fac…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD03.c</code> の詳細</summary>

- **必要な variant**:
  - `v-bdd3ed37f0` 期限切れ証明書で署名
  - `v-49e86093e7` not-yet-valid 証明書で署名
  - `v-ae2ddb08aa` KeyUsage が digitalSignature を含まない証明書で署名
  - `v-9f36507189` critical extension 付き証明書で署名
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[330, 457)` `sha256:0cb7cf156c40…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD03.d</code> の詳細</summary>

- **必要な variant**:
  - `v-b7e6dd766f` ソース A に鍵 K を紐づけ、同じ鍵 K で署名したソース B を配布 → B が拒否されるか
- **対照（negative control）**:
  - ソース A が受理されることを対照に置く
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[459, 541)` `sha256:cff82511463a…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD03.e</code> の詳細</summary>

- **必要な variant**:
  - `v-60dc7f87fb` 裸の公開鍵（RSAKeyValue）で設定
  - `v-42a220b9a3` X.509 証明書に格納した鍵で設定
- **対照（negative control）**:
  - どちらの形式でも設定できることを見る。証明書形式しか受け付けない実装は .e の許容範囲だが .c と併せて評価する
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[276, 325)` `sha256:d3aa92e8b211…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD04

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD04) ／ 節ダイジェスト `sha256:3239311e8332…` ／ 節長 635 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD04.a` | MUST | idp/sp | `CONFIG` | — | core | ルート要素に validUntil 属性がないメタデータを拒否できる |
| `IIP-MD04.b` | MUST | idp/sp | `CONFIG` | — | core | ルート要素の validUntil が過去のメタデータを拒否できる |
| `IIP-MD04.c` | MUST | idp/sp | `CONFIG` | — | core | ルート要素の validUntil が遠すぎる未来のメタデータを拒否できる（閾値は設定可能） |

<details><summary><code>IIP-MD04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-c1f4c2ad09` variant=no-validuntil
- **対照（negative control）**:
  - validUntil ありの正常 variant を対照に置く
- **設定不能時の意味**: `normative_capability`
- **注記**: 非規範の注記に『この要件はルート要素にのみ適用される』とある。子要素の validUntil は SAML2Meta に従う。
- **source_clauses**: `[0, 106)` `sha256:bf7b40e687da…` , `[107, 166)` `sha256:8effeb7d5621…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD04.b</code> の詳細</summary>

- **必要な variant**:
  - `v-58140e1e12` variant=expired (now-24h)
- **対照（negative control）**:
  - 有効な validUntil の正常 variant を対照に置く
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 106)` `sha256:bf7b40e687da…` , `[167, 258)` `sha256:2b13ca2967eb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD04.c</code> の詳細</summary>

- **必要な variant**:
  - `v-f65daa765c` 対象側で閾値 T を設定させ、now+T-δ（受理されるべき）
  - `v-a58b093589` now+T+δ（拒否されるべき）
- **対照（negative control）**:
  - Samlier は絶対閾値を持たない。対象の設定閾値の境界値ペアで判定する
- **設定不能時の意味**: `normative_capability`
- **注記**: 閾値が設定可能であること自体が義務に含まれる。設定機能がなければ outcome=violated / capability_absent。
- **source_clauses**: `[0, 106)` `sha256:bf7b40e687da…` , `[259, 421)` `sha256:850cfc185ebb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD05

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD05) ／ 節ダイジェスト `sha256:179e60c42ba2…` ／ 節長 762 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD05.a` | MUST | idp/sp | `CONFIG` | — | core | SAML V2.0 Metadata（Errata 反映）に定義されたメタデータに対応 |
| `IIP-MD05.b` | MUST | idp/sp | `CONFIG` | — | core | SAML V2.0 Metadata Schema に適合したメタデータに対応 |
| `IIP-MD05.c` | MUST | idp/sp | `CONFIG` | — | core | SAML V2.0 Metadata Interoperability Profile に対応 |
| `IIP-MD05.d` | MUST | idp/sp | `CONFIG` | — | core | Entity Attributes 拡張に対応 |
| `IIP-MD05.e` | MUST | idp/sp | `CONFIG` | — | core | Algorithm Support 拡張に対応 |
| `IIP-MD05.f` | MUST | idp/sp | `CONFIG` | — | core | Login and Discovery UI 拡張 (mdui) に対応 |
| `IIP-MD05.g` | MUST_NOT | idp/sp | `CONFIG` | — | core | その他の拡張内容が存在してもメタデータの消費・利用を妨げてはならない |

<details><summary><code>IIP-MD05.a</code> の詳細</summary>

- **必要な variant**:
  - `v-a1abd52b12` 基本 EntityDescriptor / RoleDescriptor 構造
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Meta`
- ⚠ **未解決**: 参照仕様 SAML2Meta の該当節を読んで規範内容を分解する。規範内容から対応を証明するに足る variant を起こす。現状は基本構造のスモーク 1 件のみ
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.b</code> の詳細</summary>

- **必要な variant**:
  - `v-9fe94830c4` スキーマ上の任意要素を含む variant
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2MD-xsd`
- ⚠ **未解決**: 参照仕様 SAML2MD-xsd の該当節を読んで規範内容を分解する。スキーマの任意要素・拡張点を列挙して variant にする
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[160, 199)` `sha256:7843eb17ece7…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.c</code> の詳細</summary>

- **必要な variant**:
  - `v-8487f69009` MDIOP に沿った鍵記述
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2MDIOP`
- ⚠ **未解決**: 参照仕様 SAML2MDIOP の該当節を読んで規範内容を分解する。鍵解決規則を分解する（IIP-MD06.a と重複する範囲の整理も必要）
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[200, 256)` `sha256:889fb918741c…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.d</code> の詳細</summary>

- **必要な variant**:
  - `v-b1ae256c32` mdattr:EntityAttributes を含む variant
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `MetaAttr`
- ⚠ **未解決**: 参照仕様 MetaAttr の該当節を読んで規範内容を分解する。EntityAttributes の構造（複数属性・複数値・未知属性）を variant にする
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[257, 318)` `sha256:2dfa8c656abb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.e</code> の詳細</summary>

- **必要な variant**:
  - `v-1bfe5128d6` alg:DigestMethod / alg:SigningMethod を含む variant
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2MetaAlgSup`
- ⚠ **未解決**: 参照仕様 SAML2MetaAlgSup の該当節を読んで規範内容を分解する。alg:DigestMethod / SigningMethod / EncryptionMethod の配置規則を分解する
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[319, 387)` `sha256:c5226e0be20d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.f</code> の詳細</summary>

- **必要な variant**:
  - `v-c57012a3f1` mdui:UIInfo を含む variant
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `MetaUi`
- ⚠ **未解決**: 参照仕様 MetaUi の該当節を読んで規範内容を分解する。mdui:UIInfo / mdui:DiscoHints の要素を列挙して variant にする
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[388, 465)` `sha256:62b5f48cfcc2…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD05.g</code> の詳細</summary>

- **必要な variant**:
  - `v-cd0b898e7c` 未知の名前空間の well-formed な拡張要素
  - `v-257e9c4a7a` mdrpi:RegistrationInfo（必須 6 仕様に含まれない実在の拡張）
- **対照（negative control）**:
  - mdrpi だけにすると mdrpi に特別対応した実装を誤って合格させる。実装が知りようのない未知名前空間を必ず併用する
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[0, 92)` `sha256:7dfacaaae6f1…` , `[657, 761)` `sha256:6fcd73094189…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD06) ／ 節ダイジェスト `sha256:745b1d02df86…` ／ 節長 1445 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD06.a` | MUST | idp/sp | `CONFIG` | — | core | Metadata Interoperability Profile に従ってメタデータを解釈・適用する |
| `IIP-MD06.b` | MUST | idp/sp | `CONFIG` | — | core | メタデータが利用可能な任意数の SAML ピアと、追加入力・個別設定なしで相互運用できる |
| `IIP-MD06.c` | MUST | idp/sp | `ATTESTED` | — | core | メタデータのみから署名・暗号処理の規則を導出でき、追加の信頼要件を課されない |

<details><summary><code>IIP-MD06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-36f04a6457` MDIOP の鍵解決規則に従うか（PKIX 検証を課していないか）
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2MDIOP`
- **注記**: trust store に関する記述（As an example, a separate trust store must not be required…）はイタリック＝非規範。義務にしない。
- ⚠ **未解決**: 参照仕様 SAML2MDIOP の該当節を読んで規範内容を分解する。「解釈と適用」の規範内容を分解する。証明書の扱いは MD12.d と重複しないよう整理する
- **source_clauses**: `[0, 151)` `sha256:85cd4426cdc6…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD06.b</code> の詳細</summary>

- **必要な variant**:
  - `v-660ebb2185` secondary_peer で 2 つ目の entityID を追加し、追加設定なしで動くか
- **対照（negative control）**:
  - 『追加入力なし』が要点。手入力が必要だった事実を利用者に申告させる
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[168, 401)` `sha256:0a652b7b66ca…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD06.c</code> の詳細</summary>

- **必要な variant**:
  - `v-32ad5ce1d6` メタデータ登録以外に信頼設定（CA 証明書のインポート等）が必要だったかを申告
- **注記**: 引用は SAML2MDIOP からのもので規範。ただし外部から直接観測しづらいため ATTESTED。
- **source_clauses**: `[403, 501)` `sha256:b2afc74e43b9…` , `[503, 789)` `sha256:910c64c040d0…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD07) ／ 節ダイジェスト `sha256:f94701b531c8…` ／ 節長 370 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD07.a` | MUST | idp/sp | `CONFIG` | — | core | 1 つの role descriptor に紐づく任意個数の署名鍵を消費・利用できる |
| `IIP-MD07.b` | MUST | idp/sp | `CONFIG` | — | core | 署名検証成功まで各署名鍵を順に試し、尽きたら検証失敗とする |

<details><summary><code>IIP-MD07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-3966a4dc94` 鍵 1 個
  - `v-f1677e314e` 鍵 2 個
  - `v-d9de9e7e74` 鍵 3 個
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 138)` `sha256:a42a2153ec96…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD07.b</code> の詳細</summary>

- **必要な variant**:
  - `v-d26517e828` 鍵 A・B を載せ B（2 番目）で署名 → 受理
  - `v-8ae3d43c8a` メタデータにない鍵で署名 → 拒否（対照）
- **対照（negative control）**:
  - 『尽きたら失敗』側の対照が必須。未登録鍵の署名を受理する実装を検出する
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[174, 369)` `sha256:148333ca84dc…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD08

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD08) ／ 節ダイジェスト `sha256:7c567a659ae4…` ／ 節長 255 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD08.a` | MUST | idp/sp | `CONFIG` | `supports_outbound_encryption`<br>(CAPABILITY_BASED) | core | outbound 暗号化に対応する実装は、1 role descriptor に紐づく任意個数の暗号鍵を消費できる |

<details><summary><code>IIP-MD08.a</code> の詳細</summary>

- **必要な variant**:
  - `v-75c65cc892` 暗号鍵 1 個
  - `v-559fb7f654` 2 個
  - `v-ab14245e74` 3 個 の Suite メタデータ variant
- **設定不能時の意味**: `normative_capability`
- **注記**: これは『ピアの複数暗号鍵を消費できるか』の義務。自身の復号鍵ロールオーバー（SP08 / IDP19）とは別物。
- **source_clauses**: `[0, 154)` `sha256:8b1c5bad9782…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD09

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD09) ／ 節ダイジェスト `sha256:4ca3e2d9fd30…` ／ 節長 348 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD09.a` | MUST | idp/sp | `ATTESTED` | — | core | 実行時構成の XML 署名・暗号の能力を公開できる |
| `IIP-MD09.b` | RECOMMENDED | idp/sp | `ATTESTED` | — | full | 推奨: 動的生成・エクスポートに対応し SAML2MetaAlgSup 形式で提供する |

<details><summary><code>IIP-MD09.a</code> の詳細</summary>

- **必要な variant**:
  - `v-ce5e99d071` 対象メタデータに alg:* 宣言があるか（静的検査）
  - `v-9e312fac6b` なければ『公開する機能があるか』を申告
- **対照（negative control）**:
  - メタデータに宣言がないだけでは違反にしない。公開する機能の有無が義務
- **source_clauses**: `[0, 153)` `sha256:a62935618447…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD09.b</code> の詳細</summary>

- **必要な variant**:
  - `v-887132dee5` alg:* が実構成から動的に生成されているかを申告
- **source_clauses**: `[154, 348)` `sha256:3827b367d3a9…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD10

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD10) ／ 節ダイジェスト `sha256:6f504687203e…` ／ 節長 849 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD10.a` | MUST | idp | `CONFIG` | `peer_declares_algorithm_support`<br>(CAPABILITY_BASED) | core | (IdP) ピアがメタデータで宣言したアルゴリズムに限定する |
| `IIP-MD10.b` | SHOULD | sp | `CONFIG` | `peer_declares_algorithm_support`<br>(CAPABILITY_BASED) | full | (SP) 同上（推奨） |

<details><summary><code>IIP-MD10.a</code> の詳細</summary>

- **必要な variant**:
  - `v-981483a1cb` Suite が SHA-256 のみ宣言 → SHA-1 署名で返さないか
  - `v-f8eaed9d89` GCM のみ宣言 → CBC で暗号化しないか
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[94, 279)` `sha256:2d1ad89d04d5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD10.b</code> の詳細</summary>

- **必要な variant**:
  - `v-de3cc5f1c3` 同上
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[94, 279)` `sha256:2d1ad89d04d5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD11

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD11) ／ 節ダイジェスト `sha256:944302bff8e7…` ／ 節長 301 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD11.a` | MUST | idp/sp | `CONFIG` | — | core | use 属性のない md:KeyDescriptor は署名・暗号の両方に有効 |

<details><summary><code>IIP-MD11.a</code> の詳細</summary>

- **必要な variant**:
  - `v-d0deb3bdbd` use なし鍵のみの variant で署名検証が通るか
  - `v-bf171e8959` 同 variant で暗号化に使われるか
- **対照（negative control）**:
  - 署名側・暗号側の両方を確認する。片方だけで PASS にしない
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[0, 128)` `sha256:561322c88cc5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-MD12

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD12) ／ 節ダイジェスト `sha256:3781bdd68fae…` ／ 節長 727 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-MD12.a` | REQUIRED | idp/sp | `CONFIG` | — | core | 任意個数の長期・自己署名エンドエンティティ証明書に対応 |
| `IIP-MD12.b` | REQUIRED | idp/sp | `CONFIG` | — | core | 期限切れ証明書に対応 |
| `IIP-MD12.c` | REQUIRED | idp/sp | `CONFIG` | — | core | 任意の digest アルゴリズムで署名された証明書に対応 |
| `IIP-MD12.d` | MUST_NOT | idp/sp | `CONFIG` | — | core | 証明書が期限切れ・not-yet-valid・critical / non-critical 拡張・usage flag 付き・任意の subject / issuer であっても、含まれる鍵の利用を妨げてはならない |

<details><summary><code>IIP-MD12.a</code> の詳細</summary>

- **必要な variant**:
  - `v-814e3d22ee` 自己署名 1 枚
  - `v-de3b2a9db4` 自己署名 3 枚
  - `v-5d3efc50f3` 有効期間 20 年
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[0, 85)` `sha256:efe1ac438853…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD12.b</code> の詳細</summary>

- **必要な variant**:
  - `v-5bbace1e8c` 期限切れ証明書
  - `v-a6bb86d4de` not-yet-valid 証明書
- **設定不能時の意味**: `test_precondition`
- **注記**: 引用された MDIOP は not yet valid / critical・non-critical extensions / usage flags も鍵利用を妨げないとする。
- **source_clauses**: `[0, 85)` `sha256:efe1ac438853…` , `[86, 124)` `sha256:be414b37d785…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD12.c</code> の詳細</summary>

- **必要な variant**:
  - `v-172aa0dc8e` SHA-1 署名証明書
  - `v-851e554dea` SHA-512 署名証明書
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[0, 85)` `sha256:efe1ac438853…` , `[126, 175)` `sha256:731dafad9c7e…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-MD12.d</code> の詳細</summary>

- **必要な variant**:
  - `v-9ae8ef22df` not-yet-valid 証明書（notBefore が未来）
  - `v-700ffaba35` critical extension を持つ証明書
  - `v-1e1c84b24d` non-critical extension を持つ証明書
  - `v-1f6f637dc9` KeyUsage が digitalSignature を含まない証明書（usage flag が用途と矛盾）
  - `v-afd1f283c7` extendedKeyUsage が SAML と無関係な証明書
  - `v-61efd6403c` subject が空の証明書
  - `v-c2c974c105` issuer が未知 CA の証明書
  - `v-dee7f60c45` 正常な証明書（対照。すべて拒否する実装を検出する）
- **対照（negative control）**:
  - ★ 各バリエーションを個別 variant にする。1 つ通しても他を拒否する実装を検出できない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2MDIOP`
- **注記**: 前版は not-yet-valid を MD12.b の注記に混ぜ、残りを注記だけにしていた。引用部分は非イタリックなので G1 の規則上は規範内容であり、拒否理由にされうる項目を全て variant にする。
- **source_clauses**: `[177, 245)` `sha256:dc1cb942899a…` , `[246, 416)` `sha256:c60e9935f8d0…` , `[431, 569)` `sha256:85ad17063115…` , `[571, 727)` `sha256:66a9a16a166f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 2.3 Common / Web Browser SSO

#### IIP-SSO01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO01) ／ 節ダイジェスト `sha256:ff1057626aaa…` ／ 節長 125 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO01.a` | MUST | idp/sp | `BROWSER` | — | core | SAML V2.0 Web Browser SSO Profile に対応 |

<details><summary><code>IIP-SSO01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-5dfe0f0110` SP-initiated 正常系
  - `v-56496ee074` IdP-initiated（unsolicited）正常系
- **参照先仕様**: `SAML2Prof`
- ⚠ **未解決**: 参照仕様 SAML2Prof#4.1 の該当節を読んで規範内容を分解する。4.1.1〜4.1.5 の規範的義務から variant を起こす。現状は正常系 2 件のみ
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO02) ／ 節ダイジェスト `sha256:32e4a914797e…` ／ 節長 98 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO02.a` | MUST | idp/sp | `BROWSER` | — | core | 認証要求について HTTP-Redirect と HTTP-POST の両バインディングに対応 |

<details><summary><code>IIP-SSO02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-ebc0e3a974` IdP: Redirect で AuthnRequest 受信
  - `v-b9e19b5e08` IdP: POST で AuthnRequest 受信
  - `v-b683e4f722` SP: SSO endpoint を Redirect のみにして発行
  - `v-a369a1a095` SP: POST のみにして発行
- **対照（negative control）**:
  - SP 側は 2 構成で発行させる。片方の観測だけでは両対応を証明できない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 98)` `sha256:32e4a914797e…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO03) ／ 節ダイジェスト `sha256:9e1f7ca1df32…` ／ 節長 90 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO03.a` | MUST | idp/sp | `BROWSER` | — | core | 認証応答の HTTP-POST バインディングに対応 |
| `IIP-SSO03.b` | MUST | idp/sp | `BROWSER` | — | core | エラー応答の HTTP-POST バインディングに対応 |

<details><summary><code>IIP-SSO03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-0f707e3cea` 成功 Response を POST で送受信
- **source_clauses**: `[0, 90)` `sha256:9e1f7ca1df32…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO03.b</code> の詳細</summary>

- **必要な variant**:
  - `v-55d60c5484` SP: Status がエラーの Response を POST → エラーとして扱うか
  - `v-ffcc77f301` IdP: 満たせない要求 → エラー Response が POST で返るか
- **対照（negative control）**:
  - SP 側でエラー Response を成功扱いしたら違反（対照）
- **source_clauses**: `[0, 90)` `sha256:9e1f7ca1df32…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO04

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO04) ／ 節ダイジェスト `sha256:23dda2a90643…` ／ 節長 102 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO04.a` | MUST | idp/sp | `BROWSER` | — | core | Assertion と Response の署名に、両方同時にもそれぞれ独立にも対応 |

<details><summary><code>IIP-SSO04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-e70255eff9` Assertion のみ署名
  - `v-46389ad86a` Response のみ署名
  - `v-a1f9128f62` 両方署名
- **対照（negative control）**:
  - 3 構成すべてを送信側・受信側の双方で確認する。1 構成の観測では検出力がない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 102)` `sha256:23dda2a90643…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO05

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO05) ／ 節ダイジェスト `sha256:b44add5bc36e…` ／ 節長 274 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO05.a` | MUST | idp/sp | `BROWSER` | — | core | persistent NameID Format に対応する |
| `IIP-SSO05.a1` | MUST | idp | `ATTESTED` | — | core | persistent NameID は擬似乱数で構成し、主体の実識別子と可視の対応を持たない |
| `IIP-SSO05.a2` | MUST_NOT | idp | `BROWSER` | — | core | persistent NameID の値は 256 文字を超えてはならない |
| `IIP-SSO05.a3` | MUST | idp | `BROWSER` | — | core | NameQualifier / SPNameQualifier / SPProvidedID が存在する場合、8.3.7 の規定どおりの値である |
| `IIP-SSO05.a4` | MUST_NOT | idp/sp | `NOT_OBSERVABLE` | — | core | persistent NameID を他プロバイダに平文で共有せず、適切な管理なしにログ等に出さない |
| `IIP-SSO05.a5` | MUST | idp | `BROWSER` | `supports_name_identifier_management`<br>(CAPABILITY_BASED) | core | SP（または affiliation）が代替識別子を設定済みなら、SPProvidedID にその最新の値を入れる |
| `IIP-SSO05.a6` | MUST | idp | `CONFIG` | `reissues_foreign_persistent_identifier`<br>(CAPABILITY_BASED) | core | 他エンティティが生成した persistent 識別子を再発行する場合、NameQualifier は元の生成者を指し続ける |
| `IIP-SSO05.a7` | MUST_NOT | idp | `CONFIG` | `reissues_foreign_persistent_identifier`<br>(CAPABILITY_BASED) | core | 他エンティティが生成した persistent 識別子を再発行する場合、NameQualifier を省略してはならない |
| `IIP-SSO05.a8` | MUST_NOT | idp | `ATTESTED` | — | core | persistent Format に「永続だが不透明でない」値を載せてはならない |
| `IIP-SSO05.b` | MUST | idp/sp | `BROWSER` | — | core | transient NameID Format に対応する |
| `IIP-SSO05.b1` | MUST_NOT | idp | `BROWSER` | — | core | transient NameID の値は 256 文字を超えてはならない |
| `IIP-SSO05.b2` | MUST | idp | `BROWSER` | — | core | transient NameID は SAML 識別子の規則（SAML2Core 1.3.4）に従って生成する |
| `IIP-SSO05.b3` | SHOULD | sp | `ATTESTED` | — | full | SP は transient NameID を不透明かつ一時的な値として扱うことが望ましい |

<details><summary><code>IIP-SSO05.a</code> の詳細</summary>

- **必要な variant**:
  - `v-409cc31391` NameIDPolicy で persistent を要求 → 同じ Format が返る
  - `v-aed424f97d` 【SP 消費側】persistent Format の NameID を受理する
- **対照（negative control）**:
  - Format の往復だけを見る義務。8.3.7 の個別規則は本要件の他の義務（IIP-SSO05.a1 以降）で検査する
- **参照先仕様**: `SAML2Core#8.3.7`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a1</code> の詳細</summary>

- **必要な variant**:
  - `v-d642fe2312` 観測した値がユーザー名・メールアドレス等を含まない（明白な違反の検出）
  - `v-5dbb2217c8` 生成方式が擬似乱数であることを申告で確認する
- **対照（negative control）**:
  - ★ 擬似乱数性そのものは 1 件の観測では判定できない。明白な違反のみ自動検出し、残りは申告
- **参照先仕様**: `SAML2Core#8.3.7`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a2</code> の詳細</summary>

- **必要な variant**:
  - `v-0f03f41642` 返された persistent NameID の長さが 256 コードポイント以下
- **対照（negative control）**:
  - IIP-G02.a の 256 文字境界と対になる（あちらは受理側、こちらは生成側）
- **参照先仕様**: `SAML2Core#8.3.7`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a3</code> の詳細</summary>

- **必要な variant**:
  - `v-1bef5a007f` NameQualifier がある場合、IdP の entityID と一致する
  - `v-b44303e924` SPNameQualifier がある場合、SP の entityID（または affiliation）と一致する
  - `v-a7d1f6c1b4` SP が代替識別子を一度も設定していなければ SPProvidedID が省略される（設定済みの場合の正方向は IIP-SSO05.a5）
  - `v-4b2177d011` secondary_peer（別 SP）では異なる値が返る（pair-wise pseudonym）
- **対照（negative control）**:
  - ★ pair-wise は 2 つの SP を対にしないと検証できない
  - ★ NameQualifier の期待値は『識別子を生成したエンティティの entityID』であって『送信者の entityID』ではない。対象が他エンティティ生成の識別子を再発行する構成は IIP-SSO05.a6 / .a7 で扱う
  - ★ NameQualifier / SPNameQualifier の省略は 8.3.7 で MAY として許されている。省略を FAIL にしないこと
- **参照先仕様**: `SAML2Core#8.3.7`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a4</code> の詳細</summary>

- **検証不能の理由**: 識別子を第三者に共有するか、ログに書くかは SAML のプロトコル面に現れない。外部からのブラックボックス試験では適合／不適合を区別できない。
- **対照（negative control）**:
  - —
- **参照先仕様**: `SAML2Core#8.3.7`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a5</code> の詳細</summary>

- **必要な variant**:
  - `v-17bd18194a` SP（Samlier）が <samlp:ManageNameIDRequest>/<samlp:NewID> で代替識別子を設定 → 以降の Assertion の SPProvidedID がその値になる
  - `v-0c1b285f7c` 代替識別子を 2 回更新 → SPProvidedID が最新の値になる（1 回目の値が残っていない）
  - `v-c71d4ea87c` secondary_peer（別 SP）が設定した代替識別子が、こちらの SP 向けの SPProvidedID に現れない（pair-wise の分離）
- **対照（negative control）**:
  - ★ 1 回設定して一致を見るだけでは『最も最近に設定された値』を検証していない。2 回更新し、古い値が残らないことを確認する
  - ★ 対象が SAML2Core 3.6 の Name Identifier Management に対応しない場合、代替識別子は成立しえないので NOT_APPLICABLE。省略側（IIP-SSO05.a3）だけが適用される
  - ★ <samlp:Terminate> は「識別子の利用終了」であって「SPProvidedID の解除」ではない（§3.6.3）。解除で省略に戻ることを期待するケースを作らない
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: 原文は「MUST contain the alternative identifier of the principal most recently set by the service provider or affiliation, if any」。if any が条件節で、設定済みなら正方向の MUST、未設定なら省略の MUST（IIP-SSO05.a3）に分岐する。前版はこの分岐の省略側しか variant に持っておらず、正方向を検査していなかった。
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a6</code> の詳細</summary>

- **必要な variant**:
  - `v-cad20b6af1` Samlier を上流 IdP・対象を Proxy・Samlier を下流 SP に置き、上流が発行した persistent NameID を対象が再発行する
  - `v-020855ffa9` 再発行された NameID の NameQualifier が上流 Samlier-IdP の entityID のまま（対象自身の entityID に書き換わっていない）
  - `v-045afbcd40` 対照: 対象が自前の persistent 識別子を新規生成して返す構成では NameQualifier は対象自身になる。これを FAIL にしない
- **対照（negative control）**:
  - ★ 『NameQualifier == 応答を送ってきた IdP の entityID』を無条件に期待する検査は、この再発行ケースを誤判定する
  - ★ 上流 Samlier-IdP と下流 Samlier-SP の 2 役を同一 Test Plan の Test Peer で演じる。testability を CONFIG にしているのは対象側の再構成が前提になるため。実行時にはブラウザ操作も要る
  - ★ 対象が Proxy として振る舞えない場合は条件が偽 → NOT_APPLICABLE。構成できないだけの場合は not_verified
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: 原文の当該文は Note that で始まるが MUST / MUST NOT を含む。同段落末尾の「Finally, note that ...」は RFC2119 キーワードを含まない再説明なので義務を起こさない。
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a7</code> の詳細</summary>

- **必要な variant**:
  - `v-0b113308aa` 再発行された Assertion の NameID に NameQualifier 属性が存在する
  - `v-de050a75fa` 対照: 対象が自前生成した識別子では、文脈から導出できる場合の省略が MAY として許される。これを FAIL にしない
- **対照（negative control）**:
  - ★ 一般規則（8.3.7）では NameQualifier は文脈から導出できるなら省略してよい。再発行の場合だけ省略が禁止される。この 2 ケースを対にしないと「常に省略しない実装」と「規則どおりの実装」を区別できない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: IIP-SSO05.a6（値の正しさ）と本義務（存在すること）は別の観測。省略されていれば a6 も満たせないが、a6 を満たす値であっても省略されうるため、存在検査を独立に持つ。
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.a8</code> の詳細</summary>

- **必要な variant**:
  - `v-22a94e73ca` 明白な違反の自動検出: 返る値がメールアドレス形式・LDAP DN 形式・申告済みユーザー識別子と一致する、のいずれでもない
  - `v-77f0fa432d` 値が社員番号・学籍番号等の業務識別子でないことを申告で確認する
- **対照（negative control）**:
  - ★ IIP-SSO05.a1（擬似乱数で構成する）と観測面は重なるが、名宛人と義務内容が違う。a1 は IdP の生成方式、本義務はプライバシー要件を持たない配備が Format を流用することへの禁止
  - ★ 不透明性は否定的性質なので自動検出できるのは明白な違反だけ。残りは申告
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: 原文「Deployments without such requirements are free to use other kinds of identifiers in their SAML exchanges, but MUST NOT overload this format with persistent but non-opaque values」。名宛人は deployment だが、適合試験の対象は「配備された対象実装」なので検査できる。
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[170, 222)` `sha256:200d6cc58795…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.b</code> の詳細</summary>

- **必要な variant**:
  - `v-c7f144b559` NameIDPolicy で transient を要求 → 同じ Format が返る
  - `v-bb8f5db306` 【SP 消費側】transient Format の NameID を受理する
- **対照（negative control）**:
  - Format の往復だけを見る義務。8.3.8 の個別規則は本要件の他の義務（IIP-SSO05.b1 以降）で検査する
- **参照先仕様**: `SAML2Core#8.3.8`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[223, 274)` `sha256:5bab1ac68cbe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.b1</code> の詳細</summary>

- **必要な variant**:
  - `v-a68419921a` 返された transient NameID の長さが 256 コードポイント以下
- **対照（negative control）**:
  - IIP-SSO05.a2 と同じ検査を transient に対して行う
- **参照先仕様**: `SAML2Core#8.3.8`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[223, 274)` `sha256:5bab1ac68cbe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.b2</code> の詳細</summary>

- **必要な variant**:
  - `v-aae29e53f8` 値が SAML 識別子の字句規則に適合する（先頭が数字でない等）
  - `v-2552085041` 2 回のログインで値が変わる（transient の意味）
- **対照（negative control）**:
  - ★ 『2 回で値が変わる』だけでは字句規則を検証していない
- **参照先仕様**: `SAML2Core#8.3.8`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[223, 274)` `sha256:5bab1ac68cbe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO05.b3</code> の詳細</summary>

- **必要な variant**:
  - `v-7c610424a4` transient NameID を永続識別子として保存していないかを申告で確認する
- **対照（negative control）**:
  - ★ SHOULD なので違反は WARNING。SP 内部の扱いは観測できない
- **参照先仕様**: `SAML2Core#8.3.8`
- **source_clauses**: `[0, 169)` `sha256:e66a1c4b4350…` , `[223, 274)` `sha256:5bab1ac68cbe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO06) ／ 節ダイジェスト `sha256:5f89f43ec523…` ／ 節長 1318 ／ 非規範スパン 3

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO06.a` | MUST | idp/sp | `CONFIG` | `setting_supported_by_implementation`<br>(CAPABILITY_BASED) | core | SAML2Prof 4.1.6 で MUST/MAY とされた各メタデータ要素のうち、対象が対応する設定について、追加入力なしでメタデータから取り込む |

<details><summary><code>IIP-SSO06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-03f60bb35b` md:IDPSSODescriptor/@WantAuthnRequestsSigned — MAY be used by an identity provider to document a requirement that requests be signed
  - `v-7b96873711` md:SPSSODescriptor/@AuthnRequestsSigned — MAY be used by a service provider to document the intention to sign all of its requests
  - `v-43efff0abe` md:KeyDescriptor use=signing — providers MAY document the key(s) used to sign requests, responses, and assertions（Errata 05 E58 により sign→signing）
  - `v-4cac5e94f1` md:KeyDescriptor use=encryption — MAY be used to document supported encryption algorithms and settings, and public keys（Errata 05 E58 により encrypt→encryption）
  - `v-87b1079472` md:SPSSODescriptor/@WantAssertionsSigned — MAY be used by a service provider to document a requirement that assertions be signed
  - `v-a7d66b92a1` md:ArtifactResolutionService — 条件付き MUST: HTTP Artifact バインディングで配送する場合、artifact issuer は少なくとも 1 つ提供しなければならない
  - `v-3b8c1d97c6` md:IDPSSODescriptor が含みうる md:NameIDFormat / md:AttributeProfile / saml:Attribute — MAY
  - `v-f9e3fcd6af` md:AttributeConsumingService — One or more ... MAY be included in its metadata（@index / @isDefault はこの要素の一部であり、個別に MAY とはされていない）
- **対照（negative control）**:
  - 各要素について Suite メタデータの値を変更し、対象の挙動が追従するかを見る
  - 追従しない要素があれば、その要素に対応する設定を対象が備えているか（IIP-SSO06 の条件 (b)）を確認してから判定する
  - ★ md:SingleSignOnService と md:AssertionConsumerService は §4.1.6 で RFC2119 キーワードを伴わずに記述されているため、IIP-SSO06 の条件 (a)（"MUST" or "MAY" と示された要素）に該当せず、この義務の対象外とする
- **被参照**: `IIP-IDP16.a` が `inherit_variants` でこの義務を取り込む。この義務の variant を編集すると `IIP-IDP16.a` のケースにも影響する
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2Prof#4.1.6`
- **注記**: Errata 05 E58 により KeyDescriptor の use 値は signing / encryption。
- **source_clauses**: `[0, 376)` `sha256:7bf32b4a620f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SSO07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SSO07) ／ 節ダイジェスト `sha256:72d0eb9146c7…` ／ 節長 1077 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SSO07.a` | OPTIONAL | idp/sp | `AUTOMATED` | — | full | 発行するメッセージ・アサーションに任意要素・属性を含めることは任意 |
| `IIP-SSO07.b` | REQUIRED | idp/sp | `BROWSER` | — | core | 未対応の任意コンテンツを含むメッセージ・アサーションを正しく処理する（SAML2Core の要素別処理規則に従い、エラーとするか無視する） |

<details><summary><code>IIP-SSO07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-a74fd792d3` 対象が任意要素を生成するかを情報として記録（判定しない）
- **対照（negative control）**:
  - 生成しないことを違反にしない
- **source_clauses**: `[75, 217)` `sha256:4502107e9167…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO07.b</code> の詳細</summary>

- **必要な variant**:
  - `v-c3c71223f7` verdict 対象: <saml:Subject> 付き AuthnRequest。SAML2Core 3.4.1.4 が一意の結果を規定する（返る Assertion の Subject が要求と strongly match する、または要求された主体を認識できなければ error <Status> を返す）。要求と異なる主体の Assertion を返したら違反
  - `v-b7935a4c4d` 情報記録のみ: <saml:Conditions>（SAML2Core 3.4.1: 'The responder MAY modify or supplement this set as it deems necessary'）
  - `v-e9efba5270` 情報記録のみ: <Scoping> / ProxyCount / <IDPList> / <RequesterID>（SAML2Core 3.4.1.2: 'the intermediary MAY examine the list and return a <Response> message with an error <Status>' — 二択）
  - `v-912088ec9a` 情報記録のみ: 無効な AssertionConsumerServiceIndex（SAML2Core 3.4.1: 'MAY return an error <Response> or it MAY use the default location' — 二択が明示）
  - `v-67f70ac118` 情報記録のみ: ProviderName / Consent — SAML2Core に処理規則の記述がない
  - `v-2d79e767d3` 対象外（IIP の他要件が specifically call out している）: <NameIDPolicy>→IIP-IDP10 / <RequestedAuthnContext>→IIP-IDP08 / ForceAuthn→IIP-IDP06 / IsPassive→IIP-IDP07 / AssertionConsumerServiceURL・ProtocolBinding・AssertionConsumerServiceIndex→IIP-IDP12 / AttributeConsumingServiceIndex→IIP-IDP04.b / <Extensions>・<Advice>→IIP-EXT01
- **対照（negative control）**:
  - 判定規則: SAML2Core が一意の結果（エラー XOR 無視）を規定する要素のみ verdict 対象にする。二択が許される要素は verdict を付けず情報記録のみとする
  - ★ 横断調査の結論: AuthnRequest の任意コンテンツのうち、IIP の他要件が call out していないものは <saml:Subject> / <saml:Conditions> / <Scoping>系 / ProviderName / Consent であり、このうち一意の処理規則を持つのは <saml:Subject> だけである
  - 原文冒頭の 'Unless specifically called out by subsequent requirements in this profile' により、他の IIP 要件が扱う要素はこの義務の対象から外れる
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: SAML2Core 3.4.1 / 3.4.1.4（saml-core-2.0-os, sha256:dc0890f8…）を直接読んで判定規則と verdict 対象を確定した。原文の非規範の例（Subject は required semantics、Conditions は optional semantics）とも一致する。
- **source_clauses**: `[219, 497)` `sha256:ea583e6744f8…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 2.4 Common / Extensibility

#### IIP-EXT01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-EXT01) ／ 節ダイジェスト `sha256:224aadd3c64e…` ／ 節長 510 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-EXT01.a` | MUST | idp/sp | `BROWSER` | — | core | well-formed な拡張をすべて正しく消費する |
| `IIP-EXT01.b1` | MAY | idp/sp | `BROWSER` | — | full | samlp:Extensions / md:Extensions / saml:Advice の内容は無視してよい |
| `IIP-EXT01.b` | MUST_NOT | idp/sp | `BROWSER` | — | core | samlp:Extensions / md:Extensions / saml:Advice の内容は無視してよいが、ソフトウェア障害を起こしてはならない |
| `IIP-EXT01.c1` | MAY | idp/sp | `BROWSER` | — | full | xsd:anyAttribute を持つ要素への未定義属性も同様に無視してよい |
| `IIP-EXT01.c` | MUST_NOT | idp/sp | `BROWSER` | — | core | 型定義に xsd:anyAttribute を持つ要素への未定義属性は無視してよいが、障害を起こしてはならない |

<details><summary><code>IIP-EXT01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-5325e3098c` 未知名前空間の拡張要素を含む正常フロー
- **source_clauses**: `[0, 77)` `sha256:fbe3e14936db…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-EXT01.b1</code> の詳細</summary>

- **必要な variant**:
  - `v-1841fdf9ed` 拡張内容が反映されないことを情報として記録（判定しない）
- **対照（negative control）**:
  - 無視されたことを違反にしない。これは許可であって義務ではない
- **source_clauses**: `[118, 211)` `sha256:b791ed59cb10…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-EXT01.b</code> の詳細</summary>

- **必要な variant**:
  - `v-5736a729fa` samlp:Extensions に未知要素
  - `v-8f806e8b57` md:Extensions に未知要素
  - `v-983298b7bb` saml:Advice に未知要素
- **対照（negative control）**:
  - 3 つの要素を個別 variant にする。判定対象は『障害を起こさないこと』のみで、内容が反映されないことを違反にしない
- **source_clauses**: `[188, 252)` `sha256:b34c8c238913…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-EXT01.c1</code> の詳細</summary>

- **必要な variant**:
  - `v-cdd0052383` 未知属性が反映されないことを情報として記録（判定しない）
- **source_clauses**: `[432, 468)` `sha256:798d677c4e7e…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-EXT01.c</code> の詳細</summary>

- **必要な variant**:
  - `v-d83f83ed6e` samlp:Extensions への未知属性
  - `v-fe7441b2a6` md:EntityDescriptor への未知属性
  - `v-250e346130` saml:Advice への未知属性
- **対照（negative control）**:
  - 未知『属性』は未知『要素』とは別経路。両方試す
- **source_clauses**: `[432, 510)` `sha256:c0b40d046c81…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 2.5 Common / Cryptographic Algorithms

#### IIP-ALG01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG01) ／ 節ダイジェスト `sha256:8d6c5d8785fe…` ／ 節長 210 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG01.a` | MUST | idp/sp | `BROWSER` | — | core | XML 署名の生成・検証で SHA-256 digest に対応 |

<details><summary><code>IIP-ALG01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-8000d594bf` Suite が SHA-256 で署名 → 検証される
  - `v-b319cbf40c` 対象が生成する署名の DigestMethod を観測
- **対照（negative control）**:
  - 生成側・検証側の双方を見る
- **source_clauses**: `[0, 161)` `sha256:b6bf2b68df09…` , `[162, 210)` `sha256:af381b64781b…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG02) ／ 節ダイジェスト `sha256:f2d0d73c3799…` ／ 節長 224 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG02.a` | MUST | idp/sp | `BROWSER` | — | core | XML 署名の生成・検証で RSA-SHA256 に対応 |

<details><summary><code>IIP-ALG02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-6067312a4a` Suite が RSA-SHA256 で署名 → 検証される
  - `v-f672223ec4` 対象が生成する SignatureMethod を観測
- **source_clauses**: `[0, 164)` `sha256:0f8cfe5f768e…` , `[165, 224)` `sha256:cd9524eeed35…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG03) ／ 節ダイジェスト `sha256:89164a697161…` ／ 節長 228 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG03.a` | SHOULD | idp/sp | `BROWSER` | — | full | ECDSA-SHA256 署名アルゴリズムに対応することが望ましい |

<details><summary><code>IIP-ALG03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-7f52757c99` EC 鍵を載せた Suite メタデータ + ECDSA-SHA256 署名
- **source_clauses**: `[0, 166)` `sha256:b1a3ee05b976…` , `[167, 228)` `sha256:01026de6d26f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG04

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG04) ／ 節ダイジェスト `sha256:a9c0ba8421c4…` ／ 節長 253 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG04.a` | MUST | idp/sp | `BROWSER` | — | core | AES128-GCM ブロック暗号に対応 |
| `IIP-ALG04.b` | MUST | idp/sp | `BROWSER` | — | core | AES256-GCM ブロック暗号に対応 |

<details><summary><code>IIP-ALG04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-f9e2354ac8` AES128-GCM で暗号化した Assertion → 復号される
  - `v-5b0c526ec5` 対象が生成する EncryptionMethod を観測
- **対照（negative control）**:
  - ALG04.b と別 variant。片方だけ対応する実装を検出する
- **source_clauses**: `[0, 149)` `sha256:13d87f311f30…` , `[150, 201)` `sha256:9f1a39f71c80…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG04.b</code> の詳細</summary>

- **必要な variant**:
  - `v-18f96c40cf` AES256-GCM で暗号化した Assertion → 復号される
- **source_clauses**: `[0, 149)` `sha256:13d87f311f30…` , `[202, 253)` `sha256:c51f189a9750…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG05

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG05) ／ 節ダイジェスト `sha256:9aec9a7a9af8…` ／ 節長 485 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG05.a` | MAY | idp/sp | `BROWSER` | — | full | 後方互換のため AES-CBC ブロック暗号に対応してよい |
| `IIP-ALG05.b` | SHOULD | idp/sp | `ATTESTED` | `supports_cbc`<br>(CAPABILITY_BASED) | full | AES-CBC に対応する実装は、その使用時に警告すべき |

<details><summary><code>IIP-ALG05.a</code> の詳細</summary>

- **必要な variant**:
  - `v-c16fcb2cd4` AES128-CBC
  - `v-dc06f9c264` AES256-CBC
- **対照（negative control）**:
  - 未対応は NOT_SUPPORTED であって違反ではない
- **source_clauses**: `[0, 176)` `sha256:989c558053ce…` , `[177, 229)` `sha256:c14640817b7b…` , `[230, 282)` `sha256:57d120372c50…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG05.b</code> の詳細</summary>

- **必要な variant**:
  - `v-342d148bbb` CBC を使う構成にしてもらい、ログ・UI・設定画面に警告が出るかを申告
- **設定不能時の意味**: `test_precondition`
- **注記**: この SHOULD は非イタリック＝規範。前版にあった『CBC が既定なら WARNING』は原文になく削除済み。
- **source_clauses**: `[434, 485)` `sha256:092e01d660bb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG06) ／ 節ダイジェスト `sha256:b69c91cb5f3c…` ／ 節長 595 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG06.a` | MUST | idp/sp | `BROWSER` | — | core | 鍵輸送 rsa-oaep-mgf1p に対応 |
| `IIP-ALG06.b` | MUST | idp/sp | `BROWSER` | — | core | 鍵輸送 rsa-oaep に対応 |
| `IIP-ALG06.c` | MUST | idp/sp | `BROWSER` | — | core | 上記 2 つの鍵輸送アルゴリズムの双方で DigestMethod sha256 と sha1 に対応 |
| `IIP-ALG06.d` | MUST | idp/sp | `BROWSER` | — | core | rsa-oaep で既定の MGF（MGF1 with SHA1）に対応 |

<details><summary><code>IIP-ALG06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-6ccc958d9f` rsa-oaep-mgf1p で鍵輸送した Assertion
- **source_clauses**: `[0, 146)` `sha256:95da6649c896…` , `[147, 203)` `sha256:9fe50ef0a275…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG06.b</code> の詳細</summary>

- **必要な variant**:
  - `v-6fc7ceea67` rsa-oaep で鍵輸送した Assertion
- **source_clauses**: `[0, 146)` `sha256:95da6649c896…` , `[204, 253)` `sha256:dcccf3518c1f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG06.c</code> の詳細</summary>

- **必要な variant**:
  - `v-8812cd0959` oaep-mgf1p + sha256
  - `v-ea0501e2c5` oaep-mgf1p + sha1
  - `v-2a76534278` rsa-oaep + sha256
  - `v-0791ed212e` rsa-oaep + sha1
- **対照（negative control）**:
  - 2 アルゴリズム × 2 digest の 4 組合せをすべて試す
- **source_clauses**: `[0, 146)` `sha256:95da6649c896…` , `[254, 357)` `sha256:3a58e24fe6c6…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG06.d</code> の詳細</summary>

- **必要な variant**:
  - `v-de0e0500ed` rsa-oaep + MGF1-SHA1（MGF を明示しない既定ケース）
- **source_clauses**: `[0, 146)` `sha256:95da6649c896…` , `[437, 595)` `sha256:3af2f3ce27e9…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG07) ／ 節ダイジェスト `sha256:44246f8fe2b7…` ／ 節長 198 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG07.a` | RECOMMENDED | idp/sp | `ATTESTED` | — | full | 推奨: RFC7457 と現行の TLS ベストプラクティスを考慮する |

<details><summary><code>IIP-ALG07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-886e696760` Suite→対象エンドポイントの TLS ハンドシェイクを観測（プロトコル版・暗号スイート）
- **対照（negative control）**:
  - ★ TLS ハンドシェイク 1 回の観測から『RFC7457 と現行ベストプラクティスを考慮した』全体は証明できない。観測結果は情報として記録し、判定は利用者の申告による
- **注記**: ★ 訂正: 前版は AUTOMATED。観測できるのは事実（使われた TLS 版・スイート）であって『考慮したか』ではない。原文も『This document is not normative with respect to TLS security』と述べる。
- **source_clauses**: `[61, 198)` `sha256:54ab3c733ee5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-ALG08

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-ALG08) ／ 節ダイジェスト `sha256:7b6623731dbb…` ／ 節長 449 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-ALG08.a` | MUST | idp/sp | `CONFIG` | — | core | 特定アルゴリズムの使用を禁止でき、設定・選択の試行が失敗するようにできる |
| `IIP-ALG08.b` | MUST | idp/sp | `CONFIG` | — | core | 禁止アルゴリズムの集合が設定可能である |
| `IIP-ALG08.c` | RECOMMENDED | idp/sp | `ATTESTED` | — | full | 推奨: 既定の禁止集合に md5 / rsa-md5 / rsa-1_5 を含む |

<details><summary><code>IIP-ALG08.a</code> の詳細</summary>

- **必要な variant**:
  - `v-7af4251496` 利用者に RSA-1.5 を禁止設定にしてもらい、RSA-1.5 で暗号化した Assertion が拒否されるか
  - `v-1cc87aacf7` 禁止していないアルゴリズムは受理されること（対照）
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 158)` `sha256:c63a3041cf05…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG08.b</code> の詳細</summary>

- **必要な variant**:
  - `v-962633cd09` 集合に追加・削除できるか
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[159, 206)` `sha256:31a9af35cd3f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-ALG08.c</code> の詳細</summary>

- **必要な variant**:
  - `v-23c9442d8d` 既定設定のまま MD5 署名 / RSA-1.5 鍵輸送を送り、拒否されるか
- **注記**: 既定で無効でなくても FAIL ではない（RECOMMENDED → WARNING）。
- **source_clauses**: `[211, 258)` `sha256:dce1af2e995e…` , `[266, 318)` `sha256:439a7fbb6a6c…` , `[329, 385)` `sha256:645b4323254d…` , `[400, 449)` `sha256:94d25a2a6e8d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 3.1 Service Provider / Web Browser SSO

#### IIP-SP01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP01) ／ 節ダイジェスト `sha256:f215d50e93db…` ／ 節長 201 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP01.a` | MUST | sp | `BROWSER` | — | core | 任意の xs:string の Name と任意の xs:anyURI の NameFormat を持つ saml:Attribute を消費できる |

<details><summary><code>IIP-SP01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-d6466e71fe` URN 形式の Name
  - `v-04a0e9cdb2` 長い OID
  - `v-9dbdb5fb73` 非 ASCII を含む Name
  - `v-44c159123b` 未知の NameFormat URI
  - `v-e9ef37aaf5` NameFormat 省略
- **対照（negative control）**:
  - 属性が届いたことの確認は ATTESTED。まず『エラーにならないこと』を自動判定する
- **source_clauses**: `[0, 201)` `sha256:f215d50e93db…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP02) ／ 節ダイジェスト `sha256:f1ed3c95c82e…` ／ 節長 363 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP02.a` | MUST | sp | `BROWSER` | — | core | テキストノードのみからなる単純内容の saml:AttributeValue を消費できる |
| `IIP-SP02.b` | MUST_NOT | sp | `BROWSER` | — | core | AttributeValue に xsi:type 属性の存在を要求してはならない |
| `IIP-SP02.c` | OPTIONAL | sp | `BROWSER` | — | full | 複合内容（入れ子 XML を含む）の AttributeValue への対応は任意 |

<details><summary><code>IIP-SP02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-7acd5d1463` 単純テキスト値
  - `v-5d403d4d3f` 空文字列の値
  - `v-208bc98d2b` 空白のみの値
- **source_clauses**: `[0, 241)` `sha256:ab4988e76741…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP02.b</code> の詳細</summary>

- **必要な variant**:
  - `v-5c8b4aadc5` xsi:type なしの AttributeValue
  - `v-69af28b725` xsi:type=xs:string ありの AttributeValue（対照）
- **対照（negative control）**:
  - xsi:type あり版が動くことを対照に置き、なし版だけ落ちる実装を検出する
- **source_clauses**: `[285, 363)` `sha256:6e6c5dcfbf2c…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP02.c</code> の詳細</summary>

- **必要な variant**:
  - `v-c0095509d9` 入れ子要素を含む AttributeValue を送り、対応状況を情報として記録
- **対照（negative control）**:
  - 未対応は NOT_SUPPORTED であって違反ではない
- **source_clauses**: `[242, 284)` `sha256:52cf69ff062a…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP03) ／ 節ダイジェスト `sha256:100e1a7d1291…` ／ 節長 181 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP03.a` | MUST | sp | `CONFIG` | — | core | samlp:NameIDPolicy 要素を持たない AuthnRequest を生成できる |
| `IIP-SP03.b` | MUST | sp | `CONFIG` | — | core | Format 属性のない NameIDPolicy を持つ AuthnRequest を生成できる |

<details><summary><code>IIP-SP03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-35158ec9f4` NameIDPolicy なしで発行させ、受信した AuthnRequest を静的検査
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 116)` `sha256:765325dd4101…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP03.b</code> の詳細</summary>

- **必要な variant**:
  - `v-48a61e4719` NameIDPolicy あり・Format なしで発行させる
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[117, 180)` `sha256:338191d91556…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP04

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP04) ／ 節ダイジェスト `sha256:9f4cd8b6cb55…` ／ 節長 421 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP04.a` | MUST | sp | `BROWSER` | — | full | [IdPDisco] に従った IdP Discovery に対応 |

<details><summary><code>IIP-SP04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-2d253b6a20` Suite が Discovery Service を演じ、リダイレクト規約に従うか
- **参照先仕様**: `IdPDisco`
- **注記**: 非規範の注記により、実際の discovery インタフェースの実装までは要求されない（単純なリダイレクト規約への対応のみ）。『discovery mechanisms SHOULD use SAML metadata…』もイタリック＝非規範なので義務にしない。
- ⚠ **未解決**: 参照仕様 IdPDisco の該当節を読んで規範内容を分解する。「simple redirection conventions」の規範内容を分解する
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP05

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP05) ／ 節ダイジェスト `sha256:f39652fbeca5…` ／ 節長 394 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP05.a` | MUST | sp | `CONFIG` | — | core | 任意のリソース URL について、任意の数の発行元 IdP からの Response を処理できる |
| `IIP-SP05.b` | MUST_NOT | sp | `CONFIG` | — | core | 複数 IdP の対応が IdP ごとに別のリソース URL を要求することによってしか実現できない、という制約であってはならない |

<details><summary><code>IIP-SP05.a</code> の詳細</summary>

- **必要な variant**:
  - `v-0b49c1fe36` secondary_peer で 2 つ目の Test IdP を登録
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 118)` `sha256:f4a511022846…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP05.b</code> の詳細</summary>

- **必要な variant**:
  - `v-f495473cb6` 同一の保護リソース R に IdP A でログイン
  - `v-48c2d01f83` セッション消去後、同じ R に IdP B でログイン
- **対照（negative control）**:
  - 同一 R での対照が必須。IdP を 2 つ登録するだけでは、IdP ごとに別 URL を要求する実装も通ってしまう
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[128, 267)` `sha256:2060c0fafbab…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP06) ／ 節ダイジェスト `sha256:2f6eca940d8c…` ／ 節長 218 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP06.a` | MUST | sp | `CONFIG` | — | core | exact 比較方式の RequestedAuthnContext を含む AuthnRequest を生成できる |
| `IIP-SP06.b` | MUST | sp | `CONFIG` | — | core | 任意個数の AuthnContextClassRef を含められる |

<details><summary><code>IIP-SP06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-a18670ad0d` Comparison=exact で発行させ静的検査
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 161)` `sha256:740dfcdcf7a5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP06.b</code> の詳細</summary>

- **必要な variant**:
  - `v-798067c58c` ClassRef 1 個
  - `v-f2deda4710` ClassRef 3 個
- **対照（negative control）**:
  - 0 個は SAML Core 上不正（ClassRef/DeclRef は 1 個以上）なので能力テストにしない
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2Core`
- **source_clauses**: `[162, 217)` `sha256:3e81e23f3e30…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP07) ／ 節ダイジェスト `sha256:10ef0528b7e8…` ／ 節長 129 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP07.a` | MUST | sp | `CONFIG` | — | core | saml:AuthnContext の内容に基づいて Assertion を受理または拒否できる |

<details><summary><code>IIP-SP07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-52f3e6f4c1` 対象に特定 ClassRef のみ受理するポリシーを設定
  - `v-6ca01ef5e0` 一致する ClassRef → 受理
  - `v-c57c50cf02` 一致しない ClassRef → 拒否
- **対照（negative control）**:
  - 受理と拒否を同一設定下で対にする。拒否だけでは全 Assertion を拒否する実装も通る
  - ★ 訂正: 前版は ATTESTED だったが、対象側の設定変更と positive / negative の browser control が定義されている以上 CONFIG が正しい。自己申告だけで Core の MUST を PASS にできてはならない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 129)` `sha256:10ef0528b7e8…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP08

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP08) ／ 節ダイジェスト `sha256:92e936a52bb0…` ／ 節長 395 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP08.a` | MUST | sp | `BROWSER` | — | core | saml:EncryptedAssertion を復号できる |
| `IIP-SP08.b` | MUST | sp | `CONFIG` | — | core | 復号鍵を 2 つ以上設定できる |
| `IIP-SP08.c` | MUST | sp | `BROWSER` | — | core | 復号成功まで各鍵を順に試し、尽きたら復号失敗とする |

<details><summary><code>IIP-SP08.a</code> の詳細</summary>

- **必要な variant**:
  - `v-082506bb6d` 1 番目の暗号鍵で暗号化 → 復号される
- **source_clauses**: `[0, 80)` `sha256:af9bf4b2235e…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP08.b</code> の詳細</summary>

- **必要な variant**:
  - `v-518b494b2d` 復号鍵を 2 つ設定できるか
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[121, 193)` `sha256:519f9396b701…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP08.c</code> の詳細</summary>

- **必要な variant**:
  - `v-788fb439a1` 2 番目の鍵で暗号化 → 復号される
  - `v-66ae839dbe` 登録外の鍵で暗号化 → 失敗する（対照）
- **source_clauses**: `[223, 394)` `sha256:b37e7cb5f264…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP09

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP09) ／ 節ダイジェスト `sha256:3f2195190fb2…` ／ 節長 824 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP09.a` | MUST | sp | `BROWSER` | — | core | Web Browser SSO のもとでディープリンクと保護リソースの直接指定可能性を維持する |
| `IIP-SP09.b` | RECOMMENDED | sp | `ATTESTED` | — | full | 推奨: SSO をまたいで POST ボディを保存する（サイズ制限の範囲で） |

<details><summary><code>IIP-SP09.a</code> の詳細</summary>

- **必要な variant**:
  - `v-0d2d4a8a02` 保護リソース URL に未認証でアクセス → SSO 後に元 URL に到達
- **対照（negative control）**:
  - 非規範の注記により、unsolicited response（IdP-initiated SSO）はこの要件の代替にならない
- **source_clauses**: `[0, 141)` `sha256:87fc6164d0a7…` , `[142, 318)` `sha256:4c34496f1345…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP09.b</code> の詳細</summary>

- **必要な variant**:
  - `v-d289956d82` 保護リソースへ POST → SSO → ボディが保存されているか
- **source_clauses**: `[332, 531)` `sha256:ad457a9b3c34…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP10

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP10) ／ 節ダイジェスト `sha256:40c0440d4c6d…` ／ 節長 114 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP10.a` | MUST_NOT | sp | `BROWSER` | — | core | 認識できない saml:Attribute の存在を理由に Response を失敗・拒否してはならない |

<details><summary><code>IIP-SP10.a</code> の詳細</summary>

- **必要な variant**:
  - `v-10e3469c40` 未知属性 1 個
  - `v-5c38531268` 未知属性 50 個
  - `v-e04eba6529` 未知属性 + 既知属性の混在
- **source_clauses**: `[0, 114)` `sha256:40c0440d4c6d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP11

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP11) ／ 節ダイジェスト `sha256:468f4e59dbba…` ／ 節長 111 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP11.a` | MUST_NOT | sp | `BROWSER` | — | core | FriendlyName を規範的に扱ったり、その値で比較したりしてはならない |

<details><summary><code>IIP-SP11.a</code> の詳細</summary>

- **必要な variant**:
  - `v-416a8f684d` 同じ Name で FriendlyName を変える
  - `v-941efe2090` FriendlyName を省く
  - `v-7497940c94` FriendlyName のみ一致し Name が異なる（受理してはならない）
- **対照（negative control）**:
  - FriendlyName だけが一致するケースを対照に置く
- **source_clauses**: `[0, 111)` `sha256:468f4e59dbba…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP12

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP12) ／ 節ダイジェスト `sha256:5ce6664100b8…` ／ 節長 484 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP12.a` | MUST_NOT | sp | `NOT_OBSERVABLE` | — | core | persistent NameID に SAML2Core 8.3.7 を超える意味・内容を要求してはならない |

<details><summary><code>IIP-SP12.a</code> の詳細</summary>

- **検証不能の理由**: 追加の意味づけを『要求するか』は設定面・内部ポリシーの性質であり、SAML のプロトコル面には現れない。不透明な識別子で 1 回ログインできても、適合／不適合を区別できない。
- **参照先仕様**: `SAML2Core#8.3.7`
- ⚠ **未解決**: 参照仕様 SAML2Core#8.3.7 の該当節を読んで規範内容を分解する。「semantics or content beyond」が何を指すかを分解する（NOT_OBSERVABLE のままでも除外理由を §8.3.7 に即して書き直す）
- **source_clauses**: `[0, 220)` `sha256:4487b42c2037…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP13

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP13) ／ 節ダイジェスト `sha256:0edbdad1b685…` ／ 節長 409 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP13.a` | MUST | sp | `CONFIG` | — | core | 未署名の samlp:Response を拒否できる |
| `IIP-SP13.b` | SHOULD | sp | `BROWSER` | — | full | 既定設定で未署名 Response を拒否することが望ましい |

<details><summary><code>IIP-SP13.a</code> の詳細</summary>

- **必要な variant**:
  - `v-89b0f5fe25` 拒否設定にしたうえで完全未署名 Response → 拒否されるか
  - `v-a2d2bada77` 署名済み Response → 受理される（対照）
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 87)` `sha256:f07ff14e90c1…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP13.b</code> の詳細</summary>

- **必要な variant**:
  - `v-7cfc7018d3` 既定設定のまま未署名 Response を送る
- **対照（negative control）**:
  - 既定で受理していても FAIL ではなく WARNING（SHOULD）。セキュリティ上重要なので UI では目立たせる
- **source_clauses**: `[88, 115)` `sha256:950fb1309efc…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 3.2 Service Provider / Single Logout

#### IIP-SP14

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP14) ／ 節ダイジェスト `sha256:443554848deb…` ／ 節長 612 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP14.a` | SHOULD | sp | `BROWSER` | — | full | SAML V2.0 SingleLogout profile に対応することが望ましい |
| `IIP-SP14.b` | MUST | sp | `BROWSER` | `claims_slo_support_sp`<br>(CLAIM_BASED) | full | SLO 対応を表明する SP は LogoutRequest を発行できなければならない |
| `IIP-SP14.c` | OPTIONAL | sp | `BROWSER` | — | full | LogoutRequest / LogoutResponse の消費は任意 |

<details><summary><code>IIP-SP14.a</code> の詳細</summary>

- **必要な variant**:
  - `v-8466714abc` SLO 正常系
- **参照先仕様**: `SAML2Prof#4.4`
- ⚠ **未解決**: 参照仕様 SAML2Prof#4.4 の該当節を読んで規範内容を分解する。SingleLogout Profile の規範的義務から variant を起こす
- **source_clauses**: `[0, 109)` `sha256:284c8f093605…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP14.b</code> の詳細</summary>

- **必要な variant**:
  - `v-1176298034` SP のログアウト操作から LogoutRequest が Suite に届くか
- **source_clauses**: `[110, 207)` `sha256:9c42b647597d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP14.c</code> の詳細</summary>

- **必要な variant**:
  - `v-169ca001ac` Suite から LogoutRequest を送る（未対応は NOT_SUPPORTED）
- **source_clauses**: `[208, 279)` `sha256:9a27ae08bafe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP15

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP15) ／ 節ダイジェスト `sha256:c44ab5ee19a9…` ／ 節長 139 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP15.a` | MUST | sp | `BROWSER` | `supports_slo_sp`<br>(CAPABILITY_BASED) | full | SLO に対応する SP は、ログアウト要求と応答の両方で HTTP-Redirect バインディングに対応 |

<details><summary><code>IIP-SP15.a</code> の詳細</summary>

- **必要な variant**:
  - `v-e9405d0d3f` LogoutRequest を Redirect で送受信
  - `v-cfe3e706a4` LogoutResponse を Redirect で送受信
- **対照（negative control）**:
  - 要求と応答を個別 variant にする
- **source_clauses**: `[0, 139)` `sha256:c44ab5ee19a9…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP16

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP16) ／ 節ダイジェスト `sha256:10a52215727f…` ／ 節長 467 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP16.a` | MUST | sp | `BROWSER` | `supports_slo_sp`<br>(CAPABILITY_BASED) | full | SLO に対応する SP は LogoutRequest 中の saml:EncryptedID を復号できる |
| `IIP-SP16.b` | MUST | sp | `CONFIG` | `supports_slo_sp`<br>(CAPABILITY_BASED) | full | （暗号化識別子用に）復号鍵を 2 つ以上設定できる |
| `IIP-SP16.c` | MUST | sp | `BROWSER` | `supports_slo_sp`<br>(CAPABILITY_BASED) | full | 識別子の復号成功まで各鍵を順に試し、尽きたら失敗とする |

<details><summary><code>IIP-SP16.a</code> の詳細</summary>

- **必要な variant**:
  - `v-75c52f0d6a` 1 番目の鍵で暗号化した EncryptedID
- **source_clauses**: `[0, 140)` `sha256:c80bcbdb664f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP16.b</code> の詳細</summary>

- **必要な variant**:
  - `v-4d5cef8e69` 鍵を 2 つ設定できるか
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[141, 338)` `sha256:90fb97968915…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP16.c</code> の詳細</summary>

- **必要な variant**:
  - `v-67f2374442` 2 番目の鍵で暗号化 → 復号される
  - `v-fa708ddb31` 登録外の鍵 → 失敗（対照）
- **source_clauses**: `[362, 466)` `sha256:f51f26e9ee37…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-SP17

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-SP17) ／ 節ダイジェスト `sha256:d18728f041da…` ／ 節長 314 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-SP17.a` | MUST | sp | `CONFIG` | `supports_slo_sp`<br>(CAPABILITY_BASED) | full | SLO に対応する SP は、SAML2Prof 4.4.5 に列挙された各要素についてピア設定をメタデータのみから取り込む |

<details><summary><code>IIP-SP17.a</code> の詳細</summary>

- **必要な variant**:
  - `v-829a7cddfb` md:SingleLogoutService（バインディングと Location）
  - `v-dc5f56916e` 識別子を暗号化する場合の md:KeyDescriptor use=encryption（アルゴリズム・設定・公開鍵）
- **対照（negative control）**:
  - 2 要素のみ。SLO エンドポイントへの追従だけで PASS にしない
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2Prof#4.4.5`
- **注記**: SAML2Prof 4.4.5（saml-profiles-2.0-os, sha256:5df9b874…）を直接読んで列挙した。4.4.5 は SingleLogoutService と、暗号化時の encryption KeyDescriptor の 2 件のみ。
- **source_clauses**: `[0, 314)` `sha256:d18728f041da…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 4.1 Identity Provider / Web Browser SSO

#### IIP-IDP01

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP01) ／ 節ダイジェスト `sha256:dfe610974de9…` ／ 節長 201 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP01.a` | MUST | idp | `CONFIG` | — | core | 任意の xs:string の Name と任意の xs:anyURI の NameFormat を持つ saml:Attribute を生成できる |

<details><summary><code>IIP-IDP01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-5c126b0c6f` URN 形式の Name を定義
  - `v-5681638198` 非 URI 形式の任意文字列 Name
  - `v-a47d3e67c9` 未知の NameFormat URI
- **対照（negative control）**:
  - 受信した属性の Name / NameFormat を Suite が静的検査できる
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 201)` `sha256:dfe610974de9…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP02

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP02) ／ 節ダイジェスト `sha256:920a5795b541…` ／ 節長 183 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP02.a` | MUST | idp | `CONFIG` | — | core | 依拠当事者の entityID に基づき、特定の属性（や値）を含めるか判断できる |

<details><summary><code>IIP-IDP02.a</code> の詳細</summary>

- **必要な variant**:
  - `v-a803b7b9eb` secondary_peer の 2 つの entityID に異なる属性リリース設定 → 返る属性集合が異なるか
- **対照（negative control）**:
  - 2 つの entityID の差で自動判定できる。1 つでは判断根拠が entityID かどうか分からない
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 183)` `sha256:920a5795b541…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP03

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP03) ／ 節ダイジェスト `sha256:f70fbcd3d70f…` ／ 節長 259 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP03.a` | MUST | idp | `CONFIG` | — | core | ピアのメタデータ中の mdattr:EntityAttributes の有無に基づき属性リリースを判断できる |

<details><summary><code>IIP-IDP03.a</code> の詳細</summary>

- **必要な variant**:
  - `v-26b638cde7` EntityAttributes あり variant
  - `v-eba09e35be` なし variant → 返る属性集合が異なるか
- **対照（negative control）**:
  - 有無の対照が必須
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 259)` `sha256:f70fbcd3d70f…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP04

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP04) ／ 節ダイジェスト `sha256:5b9aa663bbde…` ／ 節長 973 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP04.a` | MUST | idp | `CONFIG` | — | core | ピアのメタデータ中の AttributeConsumingService / RequestedAttribute（isRequired の値を含む）に基づき属性リリースを判断できる |
| `IIP-IDP04.b` | MUST | idp | `CONFIG` | — | core | AuthnRequest の AttributeConsumingServiceIndex により対応する AttributeConsumingService を選択できる |

<details><summary><code>IIP-IDP04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-b6e2acbe98` RequestedAttribute の有無
  - `v-7f3389b80c` isRequired=true / false の対照
- **対照（negative control）**:
  - 原文は isRequired を『判断材料にできる能力』を要求するのみで、true なら必ずリリース等の結果は規定していない。対象側で isRequired により差が出るポリシーを設定させたうえで差を観測する
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 351)` `sha256:09a0490b28aa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP04.b</code> の詳細</summary>

- **必要な variant**:
  - `v-5a322bc494` index=0 と index=1 の AttributeConsumingService を用意し、AuthnRequest の index を変えて返る属性集合が変わるか
- **対照（negative control）**:
  - Suite 起点なので完全に自動判定できる
- **設定不能時の意味**: `test_precondition`
- **source_clauses**: `[352, 553)` `sha256:1dfb85471b7a…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP05

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP05) ／ 節ダイジェスト `sha256:cdc106db01a2…` ／ 節長 494 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP05.a` | MUST | idp | `AUTOMATED` | — | core | エラー時に適切な status code を伴う samlp:Response を発行する（user agent が利用可能で、応答を届ける acceptable location が既知である場合） |

<details><summary><code>IIP-IDP05.a</code> の詳細</summary>

- **必要な variant**:
  - `v-ec22175175` 未知の NameIDPolicy/@Format → エラー Response
  - `v-4ffc648dd6` 満たせない RequestedAuthnContext → NoAuthnContext
  - `v-b890c29154` IsPassive でセッションなし → NoPassive
- **対照（negative control）**:
  - 未登録 ACS URL を FAIL 条件に使ってはならない。その場合『acceptable location』が既知でなく、エラー Response を返さないことが原文で許される
- **参照先仕様**: `SAML2Core`
- **注記**: acceptability の基準は原文で形式化されておらず IdP のポリシー次第と明記されている。
- **source_clauses**: `[0, 258)` `sha256:4b26a1ff778d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP06) ／ 節ダイジェスト `sha256:9f0c9ea1d83d…` ／ 節長 925 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP06.a` | MUST | idp | `BROWSER` | — | core | SAML2Core に定義された AuthnRequest の ForceAuthn 属性に対応 |
| `IIP-IDP06.b` | MUST | idp | `ATTESTED` | — | core | 実装内の認証機構が ForceAuthn の指示にアクセスでき、その値によって動作を変えられる |

<details><summary><code>IIP-IDP06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-e8ce359896` セッション確立後に ForceAuthn=true → 新規認証が行われ AuthnInstant が更新される
- **対照（negative control）**:
  - SAML2Core が禁じるのは true のときに既存のセキュリティコンテキストに依拠すること。false / 省略時の自主的な再認証は禁止されていないので判定に使わず advisory に記録する
- **参照先仕様**: `SAML2Core#3.4.1`
- ⚠ **未解決**: 参照仕様 SAML2Core#3.4.1 の該当節を読んで規範内容を分解する。true のとき既存セキュリティコンテキストに依拠しない／IsPassive 併用時の MUST NOT を variant に反映する
- **source_clauses**: `[0, 116)` `sha256:40cd2c53a3cb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP06.b</code> の詳細</summary>

- **必要な variant**:
  - `v-0f97f65957` 認証機構（フォーム / MFA / 証明書等）が ForceAuthn を参照できるかを申告
- **注記**: 外部からは true 時の再認証としてしか観測できないため、機構側の到達性は ATTESTED。
- **source_clauses**: `[117, 271)` `sha256:928b3a562d85…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP07) ／ 節ダイジェスト `sha256:50adf1500e90…` ／ 節長 115 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP07.a` | MUST | idp | `BROWSER` | — | core | SAML2Core に定義された IsPassive 属性に対応 |

<details><summary><code>IIP-IDP07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-bd1d99871b` セッションあり + IsPassive=true → 無操作で Assertion
  - `v-fd7a3c1375` セッションなし + IsPassive=true → NoPassive エラー
- **対照（negative control）**:
  - 2 状態の対照が必須。片方だけでは対応を証明できない
- **参照先仕様**: `SAML2Core#3.4.1`
- ⚠ **未解決**: 参照仕様 SAML2Core#3.4.1 の該当節を読んで規範内容を分解する。IdP と user agent が UI を可視的に奪わない、という規範内容を variant に反映する
- **source_clauses**: `[0, 115)` `sha256:50adf1500e90…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP08

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP08) ／ 節ダイジェスト `sha256:a569f8d22b05…` ／ 節長 148 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP08.a` | MUST | idp | `BROWSER` | — | core | AuthnRequest の RequestedAuthnContext の exact 比較方式に対応 |

<details><summary><code>IIP-IDP08.a</code> の詳細</summary>

- **必要な variant**:
  - `v-c57e1f72aa` 満たせる ClassRef → 一致した AuthnContextClassRef が返る
  - `v-3c117e99ca` 満たせない ClassRef → NoAuthnContext エラー
- **対照（negative control）**:
  - 満たせる／満たせないの対照が必須
- **参照先仕様**: `SAML2Core#3.3.2.2.1`
- **source_clauses**: `[0, 148)` `sha256:a569f8d22b05…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP09

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP09) ／ 節ダイジェスト `sha256:3950d82bd15d…` ／ 節長 123 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP09.a` | MUST | idp | `CONFIG` | — | core | Assertion の暗号化に対応 |
| `IIP-IDP09.b` | OPTIONAL | idp | `BROWSER` | — | full | 識別子・属性の暗号化は任意 |

<details><summary><code>IIP-IDP09.a</code> の詳細</summary>

- **必要な variant**:
  - `v-7b182fa6f2` Suite メタデータに暗号鍵を載せた状態で EncryptedAssertion が返るか
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 57)` `sha256:082229255930…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP09.b</code> の詳細</summary>

- **必要な variant**:
  - `v-9b90c4f9c2` EncryptedID / EncryptedAttribute が返るかを情報として記録
- **source_clauses**: `[58, 123)` `sha256:8c57da38ea1a…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP10

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP10) ／ 節ダイジェスト `sha256:02dce7a982bb…` ／ 節長 124 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP10.a` | MUST | idp | `BROWSER` | — | core | AuthnRequest の samlp:NameIDPolicy に対応 |

<details><summary><code>IIP-IDP10.a</code> の詳細</summary>

- **必要な variant**:
  - `v-27e8202f84` Format 指定
  - `v-0f374fddd6` AllowCreate=true / false
  - `v-5cd136abd8` SPNameQualifier 指定
  - `v-54bc462a9c` 要求と異なる Format を返さないこと（対照）
- **参照先仕様**: `SAML2Core#3.4.1.1`
- ⚠ **未解決**: 参照仕様 SAML2Core#3.4.1.1 の該当節を読んで規範内容を分解する。Format / SPNameQualifier / AllowCreate の規範内容を variant に反映する
- **source_clauses**: `[0, 124)` `sha256:02dce7a982bb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP11

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP11) ／ 節ダイジェスト `sha256:83f67b30db0d…` ／ 節長 137 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP11.a` | MUST | idp | `CONFIG` | — | core | Subject に NameID を含まない Assertion を生成できる |

<details><summary><code>IIP-IDP11.a</code> の詳細</summary>

- **必要な variant**:
  - `v-5609d8641f` NameID を出さない設定で SSO → Subject に NameID がないこと
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[0, 137)` `sha256:83f67b30db0d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP12

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP12) ／ 節ダイジェスト `sha256:3662ba485eda…` ／ 節長 247 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP12.a` | MUST | idp | `BROWSER` | — | core | AuthnRequest の AssertionConsumerServiceURL / ProtocolBinding / AssertionConsumerServiceIndex により応答先とバインディングを特定できる |

<details><summary><code>IIP-IDP12.a</code> の詳細</summary>

- **必要な variant**:
  - `v-4876c65668` Index 指定 → 対応する ACS に返る
  - `v-1d14cad825` URL + Binding 指定 → そこに返る
  - `v-38d1449975` メタデータにない ACS URL 指定 → 拒否される（対照。IIP-IDP05 の acceptable location と整合）
- **対照（negative control）**:
  - 3 属性を個別 variant にする
- **参照先仕様**: `SAML2Core#3.4.1`
- ⚠ **未解決**: 参照仕様 SAML2Core#3.4.1 の該当節を読んで規範内容を分解する。Index 無効時は MAY error or default／URL 指定時は responder MUST ensure the value is associated with the requester／相互排他 を variant に反映する
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 4.2 Identity Provider / Enhanced Client or Proxy

#### IIP-IDP13

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP13) ／ 節ダイジェスト `sha256:48b976641b3d…` ／ 節長 449 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP13.a` | MUST | idp | `AUTOMATED` | `not_token_translation_proxy`<br>(CLASSIFICATION_BASED) | full | SAML V2.0 Enhanced Client or Proxy Profile v2.0 に対応 |
| `IIP-IDP13.b` | OPTIONAL | idp | `ATTESTED` | `not_token_translation_proxy`<br>(CLASSIFICATION_BASED) | full | ECP Profile への完全準拠は任意 |
| `IIP-IDP13.c` | MUST | idp | `AUTOMATED` | `not_token_translation_proxy`<br>(CLASSIFICATION_BASED) | full | ECP で Bearer subject confirmation をサポートする |
| `IIP-IDP13.d` | MUST | idp | `AUTOMATED` | `not_token_translation_proxy`<br>(CLASSIFICATION_BASED) | full | ECP で channel bindings の検証に対応 |

<details><summary><code>IIP-IDP13.a</code> の詳細</summary>

- **必要な variant**:
  - `v-da292a062f` PAOS ACS を含む Suite メタデータで ECP 往復が成立する
- **参照先仕様**: `SAML2ECP`
- **注記**: 原文末尾に『This requirement does not apply to token translation Proxies.』の適用除外がある。また『excepting IIP-SSO02 and IIP-SSO03』により ECP 実行時は Redirect/POST バインディング要件が適用されない。
- ⚠ **未解決**: 参照仕様 SAML2ECP の該当節を読んで規範内容を分解する。Bearer / channel bindings 以外の規範内容の扱いを決める（Full conformance は OPTIONAL だが、対応表明した範囲の扱い）
- **source_clauses**: `[0, 102)` `sha256:01ca8c2a74c0…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP13.b</code> の詳細</summary>

- **必要な variant**:
  - `v-0e3292a3ad` 完全準拠を主張するかを情報として記録（判定しない）
- **対照（negative control）**:
  - 完全準拠していないことを違反にしない。MUST なのは .c と .d のみ
- **参照先仕様**: `SAML2ECP`
- **source_clauses**: `[103, 131)` `sha256:35392bd66ad5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP13.c</code> の詳細</summary>

- **必要な variant**:
  - `v-72ff8542a1` SubjectConfirmation/@Method=bearer であること
  - `v-c320ccc42c` @Recipient が Suite の PAOS ACS と一致すること
- **参照先仕様**: `SAML2ECP`
- **source_clauses**: `[133, 195)` `sha256:01939572cebe…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP13.d</code> の詳細</summary>

- **必要な variant**:
  - `v-110b483e7c` 一致 + 署名済み AuthnRequest → 成功し、cb:ChannelBindings が SOAP ヘッダと saml:Advice の両方に返る
  - `v-4bcf1acadf` 不一致 → エラー Response
  - `v-6eac3baf91` AuthnRequest 側のみ存在
  - `v-00da03b57d` SOAP ヘッダ側のみ存在
  - `v-6bf13c0e8b` channel bindings 使用時に AuthnRequest が未署名 → エラー Response
- **対照（negative control）**:
  - 成功ケースは『成功した』だけでなく両方への出力まで確認する。片方だけなら違反
- **参照先仕様**: `SAML2ECP#2.3.6.2`
- **source_clauses**: `[196, 232)` `sha256:66ae2da5ebad…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP14

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP14) ／ 節ダイジェスト `sha256:9ee0ba91ea64…` ／ 節長 158 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP14.a` | MUST | idp | `AUTOMATED` | — | full | user agent の認証に HTTP Basic 認証を使えること |
| `IIP-IDP14.b` | MAY | idp | `ATTESTED` | — | full | 他の認証方式に対応してもよい |

<details><summary><code>IIP-IDP14.a</code> の詳細</summary>

- **必要な variant**:
  - `v-dbc90151df` ECP 往復で Basic 認証
- **対照（negative control）**:
  - 資格情報は Run スコープのメモリのみ。outbox payload・CaseState・Transcript に書かない
- **注記**: IIP-IDP13 末尾の token translation Proxy 適用除外は IIP-IDP13 の節にのみ属し、この要件には及ばない（無条件 MUST）。
- **source_clauses**: `[0, 110)` `sha256:76629288ec58…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP14.b</code> の詳細</summary>

- **必要な variant**:
  - `v-4f2bc215f5` 対応方式を情報として記録
- **source_clauses**: `[111, 158)` `sha256:5ea3eb5da6c8…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP15

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP15) ／ 節ダイジェスト `sha256:d68561769b7a…` ／ 節長 121 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP15.a` | MUST | idp | `AUTOMATED` | — | full | [SAML-EC] 5.3.1 に従いランダム鍵を生成・同梱する |

<details><summary><code>IIP-IDP15.a</code> の詳細</summary>

- **必要な variant**:
  - `v-e5ebf8a047` Assertion の <saml:Advice> 内に <samlec:GeneratedKey> がある
  - `v-a6475a34d2` <samlec:GeneratedKey> の値が base64 で、擬似乱数として十分な長さがある
  - `v-1d6f9d9d72` ★ IdP が Assertion を暗号化している（§5.3.1: The identity provider MUST encrypt the assertion）
  - `v-7ccd52f3e6` ★ 同要素のコピーが IdP→client の SOAP ヘッダブロックにも入っている（§5.3.1: A copy of the element is also added as a SOAP header block）
  - `v-2e0e58cfc2` 複数 Assertion を返す場合、いずれか 1 つに含まれていればよい
- **対照（negative control）**:
  - 通常の ECP + HTTP Basic 往復では検証できない。SAML-EC 拡張クライアントの別ケースが要る
  - ★ Advice 内だけを見ると、SOAP ヘッダを出さない実装や Assertion を暗号化しない実装が PASS する
- **参照先仕様**: `SAML-EC#5.3.1`
- **注記**: 参照先は ECP Profile ではなく IETF kitten の SAML Enhanced Client SASL and GSS-API Mechanisms（draft-ietf-kitten-sasl-saml-ec-16）。版を specs.yaml に固定する。
- **source_clauses**: `[0, 121)` `sha256:d68561769b7a…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP16

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP16) ／ 節ダイジェスト `sha256:0e06ebedf8f5…` ／ 節長 237 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP16.a` | MUST | idp | `CONFIG` | — | full | [SAML2ECP] 2.3.10 に列挙された各要素についてピア設定をメタデータのみから取り込む |

<details><summary><code>IIP-IDP16.a</code> の詳細</summary>

- **必要な variant**:
  - `v-3464a8d5b9` ★ §2.3.10 冒頭が継承する Web Browser SSO §4.1.6 の全要素（IIP-SSO06.a の variant 一式をそのまま適用する）
  - `v-13ac186e94` md:AssertionConsumerService Binding=PAOS
  - `v-23ad8b1ba3` md:SingleSignOnService Binding=SOAP
  - `v-986fdd0cb6` cb:supportsChannelBindings（両エンドポイント）
  - `v-604094e8b6` HoK 対応時の hoksso:ProtocolBinding（SOAP / PAOS。条件付き）
  - `v-e1d6c5e615` HoK 対応時の holder-of-key browser バインディング
  - `v-d04e147196` ACS の index / isDefault
- **対照（negative control）**:
  - ecp:Response/@AssertionConsumerServiceURL がメタデータの PAOS ACS と一致するかを検証する
  - ★ ECP 固有要素だけでは §2.3.10 冒頭が継承する §4.1.6 の対象を落とす
- **参照取り込み**: `IIP-SSO06.a`（`inherit_variants` / variant 8 件）— IIP-IDP16（§2.3.10）冒頭が Browser SSO §4.1.6 の規則を ECP に継承するため、IIP-SSO06.a の required_variants を ECP 文脈でも覆う必要がある。role / level / condition / testability は本義務のものを使う
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2ECP#2.3.10`
- **source_clauses**: `[0, 237)` `sha256:0e06ebedf8f5…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

### 4.3 Identity Provider / Single Logout

#### IIP-IDP17

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP17) ／ 節ダイジェスト `sha256:b8fbb3dc6012…` ／ 節長 273 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP17.a` | MUST | idp | `BROWSER` | — | full | SAML V2.0 SingleLogout profile に対応 |
| `IIP-IDP17.b` | MUST | idp | `BROWSER` | — | full | SAML V2.0 Asynchronous Single Logout Protocol Extension に対応 |
| `IIP-IDP17.c` | OPTIONAL | idp | `BROWSER` | — | full | 他のセッション参加者へのログアウト伝播は任意 |

<details><summary><code>IIP-IDP17.a</code> の詳細</summary>

- **必要な variant**:
  - `v-cd04ba948e` SP-initiated SLO
  - `v-040fae99fd` IdP-initiated SLO
- **参照先仕様**: `SAML2Prof#4.4`
- ⚠ **未解決**: 参照仕様 SAML2Prof#4.4 の該当節を読んで規範内容を分解する。IdP 側の規範的義務から variant を起こす
- **source_clauses**: `[0, 107)` `sha256:0649b5f4937d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP17.b</code> の詳細</summary>

- **必要な variant**:
  - `v-723b93db6c` asyncslo:Asynchronous 拡張付き LogoutRequest → LogoutResponse を返さずセッションが終了する
- **対照（negative control）**:
  - 拡張なしの LogoutRequest には LogoutResponse が返ることを対照に置く
- **参照先仕様**: `SAML2ASLO`
- ⚠ **未解決**: 参照仕様 SAML2ASLO の該当節を読んで規範内容を分解する。aslo:Asynchronous の配置・LogoutResponse を返さない条件を分解する
- **source_clauses**: `[109, 184)` `sha256:b6bba3e952da…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP17.c</code> の詳細</summary>

- **必要な variant**:
  - `v-952fb39f40` secondary_peer を 2 つ目の SP として登録し、伝播が起きるかを情報として記録
- **source_clauses**: `[186, 273)` `sha256:7c03e19402eb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP18

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP18) ／ 節ダイジェスト `sha256:4874105bfdab…` ／ 節長 92 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP18.a` | MUST | idp | `BROWSER` | — | full | ログアウト要求で HTTP-Redirect バインディングに対応 |
| `IIP-IDP18.b` | MUST | idp | `BROWSER` | — | full | ログアウト応答で HTTP-Redirect バインディングに対応 |

<details><summary><code>IIP-IDP18.a</code> の詳細</summary>

- **必要な variant**:
  - `v-b991f88df5` Suite が Redirect で LogoutRequest を送る → 受理される
  - `v-0757f8ab29` IdP が Redirect で LogoutRequest を送ってくる
- **source_clauses**: `[0, 92)` `sha256:4874105bfdab…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP18.b</code> の詳細</summary>

- **必要な variant**:
  - `v-625bb5d8dd` IdP が Redirect で LogoutResponse を返す
  - `v-3cedb75b42` Suite が Redirect で LogoutResponse を返す → 受理される
- **対照（negative control）**:
  - 要求と応答を個別義務にする。片方のみ対応する実装を検出する
- **source_clauses**: `[0, 92)` `sha256:4874105bfdab…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP19

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP19) ／ 節ダイジェスト `sha256:12344a291d09…` ／ 節長 421 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP19.a` | MUST | idp | `BROWSER` | — | full | LogoutRequest 中の saml:EncryptedID を復号できる |
| `IIP-IDP19.b` | MUST | idp | `CONFIG` | — | full | 復号鍵を 2 つ以上設定できる |
| `IIP-IDP19.c` | MUST | idp | `BROWSER` | — | full | 識別子の復号成功まで各鍵を順に試し、尽きたら失敗とする |

<details><summary><code>IIP-IDP19.a</code> の詳細</summary>

- **必要な variant**:
  - `v-ba56b87891` 1 番目の鍵で暗号化した EncryptedID
- **source_clauses**: `[0, 93)` `sha256:0034ef3c7104…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP19.b</code> の詳細</summary>

- **必要な variant**:
  - `v-5a7f569a98` 鍵を 2 つ設定できるか
- **設定不能時の意味**: `normative_capability`
- **source_clauses**: `[94, 208)` `sha256:0b31c5b6d868…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP19.c</code> の詳細</summary>

- **必要な variant**:
  - `v-cf2eefed32` 2 番目の鍵で暗号化 → 復号される
  - `v-5abd614909` 登録外の鍵 → 失敗（対照）
- **source_clauses**: `[209, 421)` `sha256:1fb52db10611…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP20

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP20) ／ 節ダイジェスト `sha256:2082fe0afdf2…` ／ 節長 267 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP20.a` | MUST | idp | `CONFIG` | — | full | SAML2Prof 4.4.5 に列挙された各要素についてピア設定をメタデータのみから取り込む |

<details><summary><code>IIP-IDP20.a</code> の詳細</summary>

- **必要な variant**:
  - `v-29d41c305f` md:SingleLogoutService（バインディングと Location）
  - `v-330e986213` 識別子を暗号化する場合の md:KeyDescriptor use=encryption（アルゴリズム・設定・公開鍵）
- **対照（negative control）**:
  - 2 要素のみ。SLO エンドポイントへの追従だけで PASS にしない
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2Prof#4.4.5`
- **注記**: SAML2Prof 4.4.5（saml-profiles-2.0-os, sha256:5df9b874…）を直接読んで列挙した。4.4.5 は SingleLogoutService と、暗号化時の encryption KeyDescriptor の 2 件のみ。
- **source_clauses**: `[0, 267)` `sha256:2082fe0afdf2…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP21

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP21) ／ 節ダイジェスト `sha256:9d1a7dcae624…` ／ 節長 492 ／ 非規範スパン 1

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP21.a` | MUST | idp | `ATTESTED` | — | full | 配備者が『大文字小文字だけが異なる識別子を別主体に割り当てる』ことを回避できる形で persistent NameID を生成できる |

<details><summary><code>IIP-IDP21.a</code> の詳細</summary>

- **必要な variant**:
  - `v-58759c8aab` 大文字小文字が衝突しない形式（base32 / hex / 小文字のみ 等）を選べるかを申告
- **対照（negative control）**:
  - 観測した 1 件の NameID の文字集合からは判定できない。UUID や Base64 でも要件は満たしうるため、文字集合を理由に WARNING を出さない（情報として記録するのみ）
- **注記**: 非規範の注記で base32 [RFC4648] が一般的な達成手段として挙げられている。
- **source_clauses**: `[0, 251)` `sha256:9ba5b6a72531…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

## G1 の状態

```
g1_state       : PENDING_REVIEW
obligations    : 147
未承認         : 147
未解決 open Q  : 18 ['IIP-MD05.a', 'IIP-MD05.b', 'IIP-MD05.c', 'IIP-MD05.d', 'IIP-MD05.e', 'IIP-MD05.f', 'IIP-MD06.a', 'IIP-SSO01.a', 'IIP-SP04.a', 'IIP-SP12.a', 'IIP-SP14.a', 'IIP-IDP06.a', 'IIP-IDP07.a', 'IIP-IDP10.a', 'IIP-IDP12.a', 'IIP-IDP13.a', 'IIP-IDP17.a', 'IIP-IDP17.b']
```

作成者は `reviewer` / `approved_at` を埋めていません。
別のレビュアーが**原文と `tests/coverage.yaml` を直接照合**して承認するまで、テスト実装に着手しません。
