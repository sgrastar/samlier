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
| 義務（obligation） | 343 |
| うち MUST_CLASS | 263 |
| うち SHOULD_CLASS | 65 |
| うち MAY_CLASS | 15 |
| 条件付き義務 | 61 |
| IdP プロファイル | 260 義務（Core 190 / Full 70） |
| SP プロファイル | 177 義務（Core 119 / Full 58） |
| 非規範（イタリック）スパン | 26 |

**Testability**

| 記号 | 意味 | 件数 |
|---|---|---|
| `AUTOMATED` | Suite と対象の直接通信で完結（ブラウザ不要） | 59 |
| `BROWSER` | 利用者のブラウザが必要 | 142 |
| `ATTESTED` | 対象内部の挙動を利用者が申告 | 42 |
| `CONFIG` | 対象側の設定変更を依頼したうえで実行 | 99 |
| `NOT_OBSERVABLE` | 外部から原理的に検証不能。ケースを作らない | 1 |

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
| `IIP-SSO01.a` | MUST | idp/sp | `BROWSER` | — | core | Web Browser SSO Profile が端から端まで成立する |
| `IIP-SSO01.b` | MUST | sp | `BROWSER` | — | core | AuthnRequest の <Issuer> が存在し、SP の entityID で、Format は省略か entity |
| `IIP-SSO01.c` | MUST_NOT | sp | `BROWSER` | — | core | AuthnRequest に <saml:Subject> を含める場合、<saml:SubjectConfirmation> を含めてはならない |
| `IIP-SSO01.d` | MUST | idp | `BROWSER` | — | core | 要求の <Subject> が指す主体を認識できない場合、エラー status を持ち assertion を含まない <Response> を返す |
| `IIP-SSO01.e` | MUST_NOT | idp | `ATTESTED` | — | core | 認証・完全性保護されていない AuthnRequest の情報を、advisory を超えて信頼してはならない |
| `IIP-SSO01.f` | MUST_NOT | idp | `BROWSER` | — | core | エラーを返す場合、<Response> に assertion を含めてはならない |
| `IIP-SSO01.g` | MUST | idp | `BROWSER` | — | core | 成功応答の <Response> は 1 つ以上の <Assertion> を含む |
| `IIP-SSO01.h` | MUST | idp | `BROWSER` | — | core | <Response> の <Issuer> は省略してよいが、存在すれば IdP の entityID で、Format は省略か entity |
| `IIP-SSO01.h1` | MUST | idp | `BROWSER` | — | core | [E17] <Response> が署名されている場合、または含まれる assertion が暗号化されている場合、<Issuer> は必須 |
| `IIP-SSO01.i` | MUST | idp | `BROWSER` | — | core | 各 <Assertion> の <Issuer> は応答 IdP の entityID で、Format は省略か entity |
| `IIP-SSO01.i1` | MUST | idp | `BROWSER` | — | core | [E26] 1 つの <Response> 内の全 assertion は同一エンティティが発行する |
| `IIP-SSO01.i2` | MUST | idp | `BROWSER` | — | core | [E26] 複数 assertion を含む場合、各 <Subject> は同一の主体を指す |
| `IIP-SSO01.j` | MUST | idp | `BROWSER` | — | core | [E26] 本 profile で消費される assertion は、bearer の <SubjectConfirmation> を持つ <Subject> を含む |
| `IIP-SSO01.k` | MUST | idp | `BROWSER` | — | core | [E26/E52] bearer の <SubjectConfirmationData> に Recipient（SP の ACS URL）と NotOnOrAfter がある |
| `IIP-SSO01.k1` | MUST_NOT | idp | `BROWSER` | — | core | bearer の <SubjectConfirmationData> に NotBefore 属性を含めてはならない |
| `IIP-SSO01.k2` | MUST | idp | `BROWSER` | — | core | AuthnRequest への応答である場合、InResponseTo が要求の ID と一致する |
| `IIP-SSO01.l` | MUST | idp | `BROWSER` | — | core | [E26] bearer assertion の集合は、主体の認証を表す <AuthnStatement> を 1 つ以上含む |
| `IIP-SSO01.l1` | MUST | idp | `BROWSER` | `supports_slo_idp`<br>(CAPABILITY_BASED) | core | [E26] SLO に対応する IdP は、すべての <AuthnStatement> に SessionIndex を含める |
| `IIP-SSO01.m` | MUST | idp | `BROWSER` | — | core | [E26] 各 bearer assertion は、SP の entityID を <Audience> とする <AudienceRestriction> を含む |
| `IIP-SSO01.n` | MUST | sp | `BROWSER` | — | core | assertion / response に付いている署名を検証する |
| `IIP-SSO01.o` | MUST | sp | `BROWSER` | — | core | bearer の <SubjectConfirmationData>/@Recipient が、実際に配送された ACS URL と一致することを検証する |
| `IIP-SSO01.p` | MUST | sp | `BROWSER` | — | core | bearer の <SubjectConfirmationData>/@NotOnOrAfter を過ぎていないことを、クロックスキューを許容しつつ検証する |
| `IIP-SSO01.q` | MUST | sp | `BROWSER` | — | core | bearer の <SubjectConfirmationData>/@InResponseTo が、自身が出した AuthnRequest の ID と一致することを検証する |
| `IIP-SSO01.r` | MUST | sp | `BROWSER` | — | core | 依拠する assertion が、その他の点でも妥当であることを検証する |
| `IIP-SSO01.r1` | MUST | sp | `BROWSER` | — | core | [E26] assertion が複数ある場合、各 assertion を独立に評価する |
| `IIP-SSO01.s` | SHOULD | sp | `BROWSER` | — | full | 妥当でない、または subject confirmation の要件を満たせない assertion は破棄することが望ましい |
| `IIP-SSO01.s1` | SHOULD_NOT | sp | `BROWSER` | — | full | 妥当でない assertion をセキュリティコンテキストの確立に使うべきでない |
| `IIP-SSO01.t` | SHOULD | sp | `ATTESTED` | — | full | SessionNotOnOrAfter に達したらセキュリティコンテキストを破棄することが望ましい |
| `IIP-SSO01.u` | MUST | idp/sp | `CONFIG` | `supports_artifact_binding`<br>(CAPABILITY_BASED) | core | HTTP Artifact バインディング使用時、artifact の解決は相互認証・完全性保護・機密性を持つ |
| `IIP-SSO01.u1` | MUST | idp | `CONFIG` | `supports_artifact_binding`<br>(CAPABILITY_BASED) | core | ArtifactResolve に対し、当該 <Response> の発行先 SP にのみメッセージを渡す |
| `IIP-SSO01.v` | MUST | idp | `BROWSER` | — | core | [E26] HTTP POST バインディングで配送する場合、各 assertion はデジタル署名で保護される |
| `IIP-SSO01.w` | MUST | sp | `BROWSER` | — | core | bearer assertion の再送を防ぐ。NotOnOrAfter までの期間、使用済み ID の集合を保持する |
| `IIP-SSO01.x` | MUST_NOT | idp | `BROWSER` | — | core | <Response> の配送に HTTP-Redirect バインディングを使ってはならない |
| `IIP-SSO01.y` | MUST_NOT | idp | `BROWSER` | `supports_unsolicited_responses`<br>(CAPABILITY_BASED) | core | unsolicited の <Response> に InResponseTo 属性を含めてはならない |
| `IIP-SSO01.y1` | SHOULD | idp | `BROWSER` | `unsolicited_acs_from_metadata`<br>(CAPABILITY_BASED) | full | メタデータを使う場合、unsolicited の <Response> は既定に指定された ACS に配送することが望ましい |
| `IIP-SSO01.z` | MAY | idp | `BROWSER` | — | full | IdP は unsolicited <Response> を配送してこの profile を開始してもよい |
| `IIP-SSO01.aa` | SHOULD | sp | `CONFIG` | — | full | [E90] SP は、必要に応じて unsolicited response の受理を無効化する手段を持つことが望ましい |
| `IIP-SSO01.ab` | SHOULD | idp/sp | `BROWSER` | `derives_url_from_relaystate`<br>(CAPABILITY_BASED) | full | [E90] RelayState から最終的に導出する URL scheme は https / http に限ることが望ましい |
| `IIP-SSO01.ac` | SHOULD | sp | `BROWSER` | `relaystate_privacy_required`<br>(CLASSIFICATION_BASED) | full | SP は RelayState に元の要求内容をできるだけ露出しないことが望ましい |
| `IIP-SSO01.ad` | RECOMMENDED | idp/sp | `BROWSER` | — | full | 要求ステップ・応答ステップの HTTP 交換は TLS 上で行うことが推奨される |
| `IIP-SSO01.ae` | MUST | idp | `CONFIG` | — | core | IdP は、SP にエラーを返す場合を除き、principal の身元を確立しなければならない |
| `IIP-SSO01.af` | MUST | sp | `AUTOMATED` | — | core | AuthnRequest/@ID は SAML 識別子の一意性要件（SAML2Core 1.3.4）に従う |
| `IIP-SSO01.ag` | MUST | idp | `BROWSER` | — | core | AuthnRequest/@Destination があれば実際の受信場所と照合し、一致しなければ要求を破棄する |
| `IIP-SSO01.ah` | MUST | idp/sp | `AUTOMATED` | — | core | SAML 拡張要素は SAML 定義以外の名前空間で修飾する |
| `IIP-SSO01.ai` | MUST | idp | `BROWSER` | — | core | AuthnRequest に XML 署名がある場合、その署名が妥当であることを検証する |
| `IIP-SSO01.aj` | MUST_NOT | idp | `BROWSER` | — | core | 要求の署名が不正な場合、その要求の内容に依拠してはならない |
| `IIP-SSO01.ak` | SHOULD | idp | `BROWSER` | — | full | 要求の署名が不正な場合、エラーを返すことが望ましい |
| `IIP-SSO01.al` | SHOULD | idp | `ATTESTED` | — | full | 署名が妥当な場合、署名者の同一性と妥当性を評価することが望ましい |
| `IIP-SSO01.am` | SHOULD | sp | `BROWSER` | — | full | 同意取得を示す @Consent を含める場合、その要求は署名されていることが望ましい |
| `IIP-SSO01.an` | MUST | idp | `BROWSER` | — | core | SAML の構文・処理規則上不正な要求に応答する場合、<StatusCode>/@Value=urn:oasis:names:tc:SAML:2.0:status:Requester の SAML 応答を返す |
| `IIP-SSO01.ao` | MUST | idp | `AUTOMATED` | — | core | IdP が割り当てる識別子（<Response>/@ID と <Assertion>/@ID）は SAML 識別子の一意性要件（SAML2Core 1.3.4）に従う |
| `IIP-SSO01.ap` | MUST | idp | `BROWSER` | — | core | 要求への応答である場合、<Response>/@InResponseTo が存在し、要求の @ID と一致する |
| `IIP-SSO01.aq` | MUST | sp | `BROWSER` | — | core | <Response>/@Destination があれば実際の受信場所と照合し、一致しなければ応答を破棄する |
| `IIP-SSO01.ar` | MUST_NOT | sp | `BROWSER` | — | core | 応答の署名が不正な場合、その応答の内容に依拠してはならない |
| `IIP-SSO01.as` | SHOULD | sp | `BROWSER` | — | full | 応答の署名が不正な場合、それをエラーとして扱うことが望ましい |
| `IIP-SSO01.at` | SHOULD | sp | `ATTESTED` | — | full | 応答の署名が妥当な場合、署名者の同一性と妥当性を評価することが望ましい |
| `IIP-SSO01.au` | SHOULD | idp | `BROWSER` | — | full | 同意取得を示す @Consent を含める場合、その応答は署名されていることが望ましい |
| `IIP-SSO01.av` | MUST | idp/sp | `CONFIG` | `emits_idplist_getcomplete`<br>(CAPABILITY_BASED) | core | <GetComplete> の URI から取得される XML は、ルートが <IDPList> で、<GetComplete> を含まない |
| `IIP-SSO01.aw` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | ProxyCount=0 で主体を直接認証できない場合、最上位 StatusCode=Responder の Response を返す |
| `IIP-SSO01.ax` | MAY | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | ProxyCount=0 で直接認証できない場合、二次 StatusCode に ProxyCountExceeded を返してよい |
| `IIP-SSO01.ay` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | 新しい AuthnRequest には、元要求の全情報を同等またはより厳しい形で含める |
| `IIP-SSO01.az` | MUST | idp | `ATTESTED` | `proxies_to_non_saml_provider`<br>(CLASSIFICATION_BASED) | core | 非 SAML の IdP にプロキシする場合、IsPassive 等の user agent 制御要素が尊重される別の手段を持つ |
| `IIP-SSO01.ba` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | 新しい AuthnRequest の ProxyCount は、元の値より少なくとも 1 小さい |
| `IIP-SSO01.bb` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | 元要求に ProxyCount がない場合、新要求には ProxyCount を含めることが望ましい |
| `IIP-SSO01.bc` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | 元要求に <IDPList> があれば、新要求にも <IDPList> を含める |
| `IIP-SSO01.bd` | MUST_NOT | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | <IDPList> から要素を削除してはならない（末尾への追加は MAY） |
| `IIP-SSO01.be` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | 新 assertion の <Subject> は、元要求の <NameIDPolicy> を満たす識別子を含む |
| `IIP-SSO01.bf` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | 新 assertion の <AuthnStatement> は、委ねた先の IdP を指す <AuthenticatingAuthority> を含む <AuthnContext> を持つ |
| `IIP-SSO01.bg` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | 元 assertion に <AuthenticatingAuthority> があれば新 assertion にも含め、新しい要素はその後ろに置くことが望ましい |
| `IIP-SSO01.bh` | MUST | idp | `ATTESTED` | `proxies_to_non_saml_provider`<br>(CLASSIFICATION_BASED) | core | 非 SAML の認証 provider には、一意な識別子値を生成する |
| `IIP-SSO01.bi` | SHOULD | idp | `ATTESTED` | `proxies_to_non_saml_provider`<br>(CLASSIFICATION_BASED) | full | 生成した識別子値は、異なる要求をまたいで時間的に一貫していることが望ましい |
| `IIP-SSO01.bj` | MUST_NOT | idp | `ATTESTED` | `proxies_to_non_saml_provider`<br>(CLASSIFICATION_BASED) | core | 生成した識別子値は、他の SAML provider が使う・生成する値と衝突してはならない |
| `IIP-SSO01.cc` | MUST | idp/sp | `AUTOMATED` | — | core | 1 つのデータオブジェクトが持つ識別子の宣言は、ちょうど 1 つでなければならない |
| `IIP-SSO01.cd` | MUST | idp/sp | `ATTESTED` | `uses_random_identifier_generation`<br>(CAPABILITY_BASED) | core | 乱数・擬似乱数で識別子を作る場合、2 つの識別子が一致する確率が 2^-128 以下である |
| `IIP-SSO01.ce` | SHOULD | idp/sp | `ATTESTED` | `uses_random_identifier_generation`<br>(CAPABILITY_BASED) | full | 同じ確率が 2^-160 以下であることが望ましい |
| `IIP-SSO01.cf` | MUST | idp/sp | `ATTESTED` | `uses_random_identifier_generation`<br>(CAPABILITY_BASED) | core | 擬似乱数生成器は、システム間の一意性を保つため一意な材料でシードする |
| `IIP-SSO01.cg` | MUST | sp | `AUTOMATED` | — | core | SP が生成する <samlp:AuthnRequest> が protocol schema に適合する（必須の @ID / @Version / @IssueInstant） |
| `IIP-SSO01.dv` | MUST | idp | `AUTOMATED` | — | core | IdP が生成する <samlp:Response> が protocol schema に適合する（必須の @ID / @Version / @IssueInstant と <samlp:Status>） |
| `IIP-SSO01.dw` | MUST | idp | `AUTOMATED` | — | core | IdP が生成する <saml:Assertion> が assertion schema に適合する（必須の @Version / @ID / @IssueInstant / <Issuer>、<AuthnStatement> の @AuthnInstant と <AuthnContext>） |
| `IIP-SSO01.dx` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | プロキシ IdP が上流へ生成する <samlp:AuthnRequest> が protocol schema に適合する |
| `IIP-SSO01.ch` | MUST | idp | `AUTOMATED` | — | core | 最上位 <StatusCode>/@Value は SAML2Core 3.2.2.2 の top-level リストの値である |
| `IIP-SSO01.ci` | MUST | idp | `AUTOMATED` | — | core | 汎用の <saml:Statement> を使う場合、実際の型を xsi:type で示す |
| `IIP-SSO01.cj` | MUST | idp | `AUTOMATED` | — | core | statement を 1 つも含まない assertion は <saml:Subject> を含む |
| `IIP-SSO01.ck` | MUST | idp | `AUTOMATED` | — | core | 汎用の <saml:Condition> を使う場合、実際の型を xsi:type で示す |
| `IIP-SSO01.cl` | MUST | idp | `AUTOMATED` | — | core | 1 つの <saml:Conditions> に <saml:OneTimeUse> は 1 つまで |
| `IIP-SSO01.cm` | MUST | idp | `AUTOMATED` | — | core | 1 つの <saml:Conditions> に <saml:ProxyRestriction> は 1 つまで |
| `IIP-SSO01.cn` | MUST | idp | `AUTOMATED` | — | core | <Conditions> に NotBefore と NotOnOrAfter が両方あるとき、NotBefore は NotOnOrAfter より前である |
| `IIP-SSO01.co` | MUST | sp | `BROWSER` | — | core | Invalid または Indeterminate と判定された assertion は拒否する |
| `IIP-SSO01.cp` | MUST | sp | `BROWSER` | — | core | 1 つの assertion に複数の <AudienceRestriction> があるとき、それぞれを独立に評価する |
| `IIP-SSO01.cq` | SHOULD | sp | `ATTESTED` | — | full | <OneTimeUse> を持つ assertion は、依拠当事者が直ちに使うことが望ましい |
| `IIP-SSO01.cr` | MUST_NOT | sp | `ATTESTED` | — | core | <OneTimeUse> を持つ assertion を将来の利用のために保持してはならない |
| `IIP-SSO01.cs` | MUST | sp | `ATTESTED` | — | core | assertion を将来利用のために保持する実装は、<OneTimeUse> を遵守しなければならない |
| `IIP-SSO01.ct` | MUST_NOT | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | <ProxyRestriction> を持つ assertion に基づいて、その制限に違反する assertion を発行してはならない |
| `IIP-SSO01.cu` | MUST_NOT | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | <ProxyRestriction>/@Count=0 の assertion に基づいて、他の依拠当事者へ assertion を発行してはならない |
| `IIP-SSO01.cv` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | <ProxyRestriction>/@Count が 0 より大きいとき、発行する assertion には Count が 1 以上小さい <ProxyRestriction> を含める |
| `IIP-SSO01.cw` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | <ProxyRestriction> に <Audience> がある場合、発行する assertion の <AudienceRestriction> は「元の <Audience> を 1 つ以上含む」かつ「元になかった <Audience> を含まない」 |
| `IIP-SSO01.cx` | MUST | sp | `BROWSER` | — | core | スキーマ上不正な assertion（必須の @Version / @IssueInstant / <Issuer> / <AuthnContext> 欠落等）を拒否する |
| `IIP-SSO01.cy` | SHOULD | idp/sp | `AUTOMATED` | — | full | 要素または Format が用法と意味を定義していない限り、NameQualifier / SPNameQualifier を付けない |
| `IIP-SSO01.cz` | SHOULD_NOT | idp | `AUTOMATED` | — | full | <saml:Subject> は 2 人以上の主体を識別すべきでない |
| `IIP-SSO01.da` | MUST_NOT | idp | `AUTOMATED` | — | core | SubjectConfirmationDataType に、名前空間なし・SAML 定義名前空間の XML 属性を拡張として足してはならない |
| `IIP-SSO01.db` | SHOULD | idp | `AUTOMATED` | — | full | <SubjectConfirmationData> の妥当期間は、assertion 全体の妥当期間（<Conditions>）の内側に両端とも収まることが望ましい |
| `IIP-SSO01.dc` | MUST | idp | `AUTOMATED` | — | core | <SubjectConfirmationData> に NotBefore と NotOnOrAfter が両方あるとき、NotBefore は NotOnOrAfter より前である |
| `IIP-SSO01.dd` | MUST | idp | `AUTOMATED` | — | core | <saml:AuthnStatement> を含む assertion は <saml:Subject> を含む |
| `IIP-SSO01.de` | SHOULD_NOT | idp | `ATTESTED` | — | full | SessionIndex の値は、異なるセッション参加者をまたいで主体の活動を相関できるものであるべきでない |
| `IIP-SSO01.df` | SHOULD | idp | `ATTESTED` | `uses_small_integer_sessionindex`<br>(CAPABILITY_BASED) | full | SessionIndex の値域は、1 つの値の濃度が十分高くなるように選ぶことが望ましい |
| `IIP-SSO01.dg` | SHOULD | idp | `ATTESTED` | `uses_small_integer_sessionindex`<br>(CAPABILITY_BASED) | full | SessionIndex はその値域からランダムに選ぶことが望ましい |
| `IIP-SSO01.dh` | MUST | idp | `AUTOMATED` | — | core | <saml:AttributeStatement> を含む assertion は <saml:Subject> を含む |
| `IIP-SSO01.di` | MUST_NOT | idp | `AUTOMATED` | — | core | AttributeType に、名前空間なし・SAML 定義名前空間の XML 属性を拡張として足してはならない |
| `IIP-SSO01.dj` | MUST | idp | `AUTOMATED` | — | core | <AttributeStatement> 内で、属性は存在するが値がない場合、<AttributeValue> を省略する |
| `IIP-SSO01.dk` | MUST | idp | `AUTOMATED` | — | core | 属性の値が空（空文字列等）の場合、対応する <AttributeValue> は空要素にする |
| `IIP-SSO01.dl` | MUST | idp | `AUTOMATED` | — | core | 属性の値が null の場合、<AttributeValue> は空要素かつ xsi:nil="true"（または "1"）を持つ |
| `IIP-SSO01.dm` | SHOULD | idp | `AUTOMATED` | — | full | 暗号化された SAML 要素の <xenc:EncryptedData>/@Type は存在することが望ましい |
| `IIP-SSO01.dn` | MUST | idp | `AUTOMATED` | — | core | @Type が存在する場合、その値は http://www.w3.org/2001/04/xmlenc#Element である |
| `IIP-SSO01.do` | MUST | idp | `AUTOMATED` | — | core | 暗号化された内容は、その暗号化要素が要求する型の要素を含む |
| `IIP-SSO01.dp` | MUST | idp | `AUTOMATED` | — | core | <saml:EncryptedID> の暗号文は、暗号化操作ごとに一意である |
| `IIP-SSO01.dq` | SHOULD | idp | `AUTOMATED` | — | full | 各 wrapped key には、鍵の暗号化先を示す Recipient 属性を含めることが望ましく、その値は SAML システム実体の URI 識別子であることが望ましい |
| `IIP-SSO01.ds` | SHOULD | idp | `AUTOMATED` | — | full | IPv4 アドレスはドット 10 進、IPv6 アドレスは RFC 3513 の表記で書くことが望ましい |
| `IIP-SSO01.du` | RECOMMENDED | idp | `AUTOMATED` | — | full | 属性が複数の離散値を持つ場合、各値を個別の <saml:AttributeValue> に置くことが推奨される |
| `IIP-SSO01.dy` | RECOMMENDED | idp | `ATTESTED` | — | full | SessionIndex の相関を防ぐ 2 つの方式のいずれかを採ることが推奨される |
| `IIP-SSO01.dz` | MUST | idp/sp | `AUTOMATED` | — | core | SAML メッセージ中の文字列は、空白以外の文字を 1 つ以上含む |
| `IIP-SSO01.ea` | MUST | idp/sp | `ATTESTED` | — | core | xs:string 型（およびその派生型）の値は、完全なバイナリ比較で比較する |
| `IIP-SSO01.eb` | MUST_NOT | idp/sp | `BROWSER` | — | core | 大文字小文字を無視した比較・空白の正規化やトリム・ロケール依存形式の変換に依拠してはならない |
| `IIP-SSO01.ec` | MUST | idp/sp | `ATTESTED` | — | core | 異なる文字符号化の値を比較する場合、双方を Unicode NFC に変換して完全バイナリ比較したのと同じ結果になる方法を使う |
| `IIP-SSO01.ed` | MUST | idp/sp | `ATTESTED` | — | core | SAML 文書のデータを外部データと比較する際、XML の正規化規則を考慮する |
| `IIP-SSO01.ee` | MUST_NOT | idp/sp | `ATTESTED` | — | core | 値の特定のソート順に依拠してはならない |
| `IIP-SSO01.ef` | MUST | idp/sp | `AUTOMATED` | — | core | SAML 定義の要素・属性で使う URI 参照値は、空白以外の文字を 1 つ以上含み、絶対 URI である |
| `IIP-SSO01.eg` | MUST | idp/sp | `AUTOMATED` | — | core | すべての xs:dateTime 値を、タイムゾーン成分のない UTC 形式で表す |
| `IIP-SSO01.eh` | SHOULD_NOT | idp/sp | `ATTESTED` | — | full | ミリ秒より細かい時間分解能に依拠すべきでない |
| `IIP-SSO01.ei` | MUST_NOT | idp/sp | `AUTOMATED` | — | core | うるう秒を指定する時刻を生成してはならない |
| `IIP-SSO01.ej` | MUST_NOT | idp | `AUTOMATED` | — | core | 対象が対応していない Major.Minor の assertion を発行してはならない |
| `IIP-SSO01.ek` | MUST_NOT | sp | `BROWSER` | — | core | 対応していない major assertion version の assertion を処理してはならない |
| `IIP-SSO01.el` | MUST_NOT | sp | `AUTOMATED` | — | core | 自分が対応していない応答バージョンに対応する要求バージョンで、要求を出してはならない |
| `IIP-SSO01.em` | MUST | idp | `BROWSER` | — | core | 対応していない major request version の要求を拒否しなければならない |
| `IIP-SSO01.en` | MUST_NOT | idp | `BROWSER` | — | core | 対応する要求より高い応答バージョンの応答を出してはならない |
| `IIP-SSO01.eo` | MUST_NOT | idp | `BROWSER` | — | core | 対応する要求より低い major 応答バージョンの応答を出してはならない（RequestVersionTooHigh の報告を除く） |
| `IIP-SSO01.ep` | MUST | idp | `BROWSER` | — | core | SAML プロトコルバージョンの非互換によるエラー応答は、最上位 <StatusCode> を VersionMismatch にする |
| `IIP-SSO01.eq` | MUST_NOT | idp | `AUTOMATED` | — | core | V1.x の assertion を V2.0 の <Response> に含めてはならない |
| `IIP-SSO01.fg` | SHOULD | sp | `AUTOMATED` | — | full | 要求元と応答元の双方が対応する最高の要求バージョンで要求を出すことが望ましい |
| `IIP-SSO01.fh` | SHOULD | sp | `ATTESTED` | — | full | 応答元の能力が不明な場合、要求元は「応答元が要求元の対応する最高版に対応している」と仮定することが望ましい |
| `IIP-SSO01.er` | MUST | idp/sp | `AUTOMATED` | — | core | assertion とプロトコルメッセージの XML 署名は enveloped でなければならない |
| `IIP-SSO01.es` | SHOULD | idp | `BROWSER` | — | full | 依拠当事者が asserting party 以外の経路で得る assertion は、asserting party が署名すべき |
| `IIP-SSO01.et` | SHOULD | idp | `BROWSER` | — | full | ブラウザ経由で SP に届く <Response> は、送信者（IdP）が署名すべき |
| `IIP-SSO01.fj` | SHOULD | sp | `BROWSER` | — | full | AuthnRequest は署名するか、配送 binding で送信者認証と完全性保護を行うことが望ましい |
| `IIP-SSO01.eu` | MUST | idp/sp | `AUTOMATED` | — | core | 対象が署名する assertion / プロトコルメッセージのルート要素に ID 属性の値を与える |
| `IIP-SSO01.ev` | MUST | idp/sp | `AUTOMATED` | — | core | 署名は単一の <ds:Reference> を含み、その URI は署名対象ルート要素の ID への same-document reference である |
| `IIP-SSO01.ew` | SHOULD | idp/sp | `AUTOMATED` | — | full | <ds:CanonicalizationMethod> と <ds:Transform> の双方で Exclusive Canonicalization を使うことが望ましい |
| `IIP-SSO01.ex` | SHOULD_NOT | idp/sp | `AUTOMATED` | — | full | 署名に enveloped-signature / exclusive c14n 以外の transform を含めるべきでない |
| `IIP-SSO01.ey` | MUST | sp | `BROWSER` | — | core | SP が許可外 transform を含む署名を拒否しない場合、<Response> / <Assertion> のどの内容も署名対象から除外されていないことを保証する |
| `IIP-SSO01.fk` | MUST | idp | `BROWSER` | — | core | IdP が許可外 transform を含む署名を拒否しない場合、<AuthnRequest> のどの内容も署名対象から除外されていないことを保証する |
| `IIP-SSO01.ez` | MUST | idp | `CONFIG` | — | core | <Assertion> を暗号化する場合、暗号データは平文と同じ位置に置き換わる |
| `IIP-SSO01.fd` | MUST | idp | `CONFIG` | — | core | <BaseID> / <NameID> を暗号化する場合、暗号データは平文と同じ位置に置き換わる |
| `IIP-SSO01.fe` | MUST | idp | `CONFIG` | — | core | <Attribute> を暗号化する場合、暗号データは平文と同じ位置に置き換わる |
| `IIP-SSO01.dr` | MUST | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | プロキシ IdP が上流へ生成する AuthnRequest の @ID も SAML 識別子の一意性要件に従う |
| `IIP-SSO01.bk` | MAY | idp | `BROWSER` | — | full | unsolicited 応答に、SP との相互合意に基づく RelayState を含めてもよい |
| `IIP-SSO01.y2` | SHOULD | sp | `BROWSER` | — | full | SP は unsolicited 応答を扱えるよう、処理成功後の既定の遷移先を用意しておくことが望ましい |
| `IIP-SSO01.fl` | SHOULD | sp | `AUTOMATED` | `allowcreate_general_interoperability_case`<br>(CLASSIFICATION_BASED) | full | AllowCreate を特定用途に使わない SP は、transient NameID を要求する場合を除き、相互運用性のため通常 true に設定することが望ましい |
| `IIP-SSO01.fm` | SHOULD | idp | `CONFIG` | `proxy_allowcreate_general_interoperability_case`<br>(CLASSIFICATION_BASED) | full | 上流へ要求する proxy IdP が AllowCreate を特定用途に使わない場合、transient NameID を要求するときを除き、通常 true に設定することが望ましい |
| `IIP-SSO01.fn` | MUST_NOT | sp | `AUTOMATED` | — | core | SP は transient NameID を要求するとき AllowCreate を使用してはならない |
| `IIP-SSO01.fo` | MUST_NOT | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | proxy IdP は上流へ transient NameID を要求するとき AllowCreate を使用してはならない |
| `IIP-SSO01.fp` | SHOULD | idp | `BROWSER` | — | full | IdP は transient NameID の要求、または transient NameID assertion の発行と併用された AllowCreate を無視することが望ましい |
| `IIP-SSO01.fr` | SHOULD | idp | `CONFIG` | — | full | assertion を Subject 以外の entity に使用させる場合、その entity を SubjectConfirmation 内で識別することが望ましい |
| `IIP-SSO01.fs` | SHOULD_NOT | idp/sp | `AUTOMATED` | — | full | SAML 署名に ds:Object 要素を含めるべきでない |
| `IIP-SSO01.ft` | SHOULD | sp | `BROWSER` | — | full | SP は ds:Object を含む Response / Assertion の署名を拒否することが望ましい |
| `IIP-SSO01.fu` | SHOULD | idp | `BROWSER` | — | full | IdP は ds:Object を含む AuthnRequest の署名を拒否することが望ましい |
| `IIP-SSO01.fv` | SHOULD | idp | `AUTOMATED` | — | full | CBC-mode の EncryptedAssertion を含む場合、Response を署名して暗号文を完全性保護することが望ましい |
| `IIP-SSO01.fw` | SHOULD | sp | `ATTESTED` | — | full | SP は CBC で暗号化された assertion または暗号データを含む assertion を処理する前に完全性保護を要求することが望ましい |
| `IIP-SSO01.fx` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | proxy IdP が上流へ送る AuthnRequest は、署名するか配送 binding で送信者認証と完全性保護を行うことが望ましい |
| `IIP-SSO01.fy` | MUST_NOT | sp | `BROWSER` | — | core | Assertion の署名が不正な場合、SP はその Assertion の内容に依拠してはならない |
| `IIP-SSO01.fz` | SHOULD | sp | `ATTESTED` | — | full | Assertion 署名が妥当な場合、SP は issuer の同一性と妥当性を評価することが望ましい |
| `IIP-SSO01.ga` | MUST | idp | `CONFIG` | — | core | RequestedAuthnContext の Comparison=minimum で成功する場合、結果は要求した context の少なくとも 1 つ以上の強度である |
| `IIP-SSO01.gb` | MUST | idp | `CONFIG` | — | core | RequestedAuthnContext の Comparison=better で成功する場合、結果は要求した context の少なくとも 1 つより強い |
| `IIP-SSO01.gc` | MUST | idp | `CONFIG` | — | core | RequestedAuthnContext の Comparison=maximum で成功する場合、要求 context の少なくとも 1 つを超えない範囲で可能な限り強い context を返す |
| `IIP-SSO01.gd` | SHOULD | idp | `CONFIG` | — | full | bearer assertion を複数の attesting entity に使用させる場合、複数の SubjectConfirmation を含めることが望ましい |
| `IIP-SSO01.ge` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | proxy IdP は自身と上流 responder の双方が対応する最高の request version を使うことが望ましい |
| `IIP-SSO01.gf` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | proxy IdP が上流 responder の能力を知らない場合、自身が対応する最高 request version を仮定することが望ましい |
| `IIP-SSO01.gg` | MUST_NOT | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | core | proxy IdP は自身が対応しない response version に対応する request version を上流へ送ってはならない |
| `IIP-SSO01.gh` | SHOULD | idp | `CONFIG` | `supports_authnrequest_proxying`<br>(CAPABILITY_BASED) | full | proxy IdP が上流 AuthnRequest に同意取得済みを示す Consent を含める場合、その要求に署名することが望ましい |
| `IIP-SSO01.gi` | MUST_NOT | idp | `BROWSER` | — | core | 要求の ID を特定できない場合、Response に InResponseTo を含めてはならない |
| `IIP-SSO01.gj` | MUST | idp | `CONFIG` | — | core | AuthnRequest の RequestedAuthnContext 候補は順序付き集合として評価し、先頭を最優先として扱う |

<details><summary><code>IIP-SSO01.a</code> の詳細</summary>

- **必要な variant**:
  - `v-bf97fb30af` SP-initiated 正常系: 保護リソース → AuthnRequest → 認証 → Response → リソース取得
  - `v-cb76d17f88` Redirect で要求 / POST で応答（IIP-SSO02・IIP-SSO03 と同じ組合せ）
  - `v-b559d1b245` POST で要求 / POST で応答
- **対照（negative control）**:
  - ★ 本義務は『profile が成立すること』だけを見る包括ケース。個々の規範句を通したことにはしない
  - ★ 正常系だけでは検出力がない。IIP-SSO01.b 以降の個別義務が対照を担う
  - ★ 訂正: 前版は『IdP-initiated（unsolicited）正常系』を必須 variant にしていたが、§4.1.5 の開始は MAY である（IIP-SSO01.z）。必須にすると unsolicited を発行しない適合 IdP を FAIL にする。unsolicited 固有の義務は IIP-SSO01.y / .y1 に条件付きで置いた
- **参照先仕様**: `SAML2Prof#4.1`
- **注記**: 【CP1 照合記録】2026-08-27 に §4.1（Errata 反映）・取り込み句 A・取り込み句 B を、欠落検査（原文→義務）と過剰検査（義務→実効原文）の双方向で再照合した。Errata の置換優先順位を確認し、E43/E93 で消えた旧 §6.2、E79 の旧 SessionNotOnOrAfter MUST、E81 の旧 RSA-SHA1 SHOULD は生成対象から除外した。以下を対応表の正本とする。  【§4.1 の RFC2119 句 → 義務の対応表】4.1.1: bearer confirmation method を使用する。E47 が bearer method に追加した intended attesting entity の識別 SHOULD → .fr、複数 attesting entity の複数 SubjectConfirmation SHOULD → .gd。4.1.2: Redirect を応答に使わない → .x ／ SP が Discovery を使ってよい（MAY）→ IIP-SP04。4.1.3.1: RelayState を使ってよい（MAY）→ .ab の注記 ／ 元要求をできるだけ露出しない（SHOULD）→ .ac。4.1.3.2: Discovery / 別サービスへの誘導 / メタデータの利用（すべて MAY）→ 権限であり義務を起こさない。4.1.3.3: TLS 上で行うことが RECOMMENDED → .ad ／ AuthnRequest の署名は MAY → 権限 ／ 『IdP MUST process the <AuthnRequest> as described in [SAMLCore]』は**取り込み句**であり、取り込まれる SAML2Core の規範句を下記【取り込み句 A】に展開した。4.1.3.4: principal の identity を確立する（MUST）→ .ae。4.1.3.5: 成功・失敗にかかわらず Response / artifact を含む HTTP 応答を生成する（SHOULD、E85 でエラー応答を強化）→ 成功側は .a・.g、エラー側は IIP-IDP05.a（IIP が MUST に強化） ／ ACS 位置が SP の管理下だと確かめる（MUST）→ IIP-IDP12.b ／ 指定された binding と ACS を可能なら honor する（MUST）→ IIP-IDP12.a・.e・.f ／ TLS が RECOMMENDED → .ad ／ POST 時の Assertion / Response 署名（MUST、E26・E93 反映）→ .v ／ CBC-mode EncryptedAssertion の Response 署名（SHOULD, E93）→ .fv ／ 『SP MUST process the <Response> as described in [SAMLCore]』は**取り込み句**で、profile 固有の処理規則は .n〜.r1、取り込まれる SAML2Core の一般規則は下記【取り込み句 B】。4.1.3.6: セキュリティコンテキストの確立は MAY → 権限。4.1.4.1: Issuer（MUST）→ .b ／ 要求を満たせないときのエラー応答（MUST）→ IIP-IDP05.a ／ Subject に SubjectConfirmation を入れない（MUST NOT）→ .c ／ 主体を認識できないときのエラー（MUST）→ .d ／ 未認証要求の情報を信頼しない（MUST NOT）→ .e ／ ACS の検証（MUST）→ IIP-IDP12.b ／ ★『SP が新規識別子の作成を望むなら AllowCreate=true を含めなければならない』という旧 Profile MUST は SAML2Errata E14 が §4.1.4.1 から削除した。一方、E14 が Core 3.4.1.1 に追加した新規則は取り込み句 A で .fl〜.fp に分解する。4.1.4.2（E17・E26・E52 反映）: .f〜.m ／ Address 属性・追加の statement・AttributeConsumingServiceIndex の無視は MAY → 権限 ／ 『条件は SP に理解・受理されなければ assertion は妥当でない』は SP 側の処理であり .r に含む。4.1.4.3（E26・E93 反映）: .n〜.t ／ CBC-mode EncryptedAssertion の Response 署名（SHOULD）→ .fv ／ Address の照合は MAY → 権限。4.1.4.4: .u・.u1。『using the Artifact Resolution profile』は artifact の参照解決機構を特定するが、この節が追加で明示する規範内容は相互認証・完全性・機密性と intended SP への限定であり、この 2 義務に分解する。Artifact Resolution Profile §5 全体を SSO01 に再帰的に二重計上しない ／ 4.1.4.5（E26 反映）: .v・.w。4.1.5（E90 追記を含む）: 開始は MAY → .z ／ InResponseTo を含めない（MUST NOT）→ .y ／ 既定 ACS への配送（SHOULD）→ .y1 ／ SP は unsolicited を扱えるようにしておく（SHOULD）→ .y2 ／ SP は unsolicited の受理を無効化できるべき（SHOULD, E90）→ .aa ／ RelayState の受け渡しは MAY → 権限。E90 が追加する新 §4.1.6『Use of Relay State』: URL scheme を https / http に限る（SHOULD）→ .ab。OS 版 §4.1.6『Use of Metadata』: IIP-SSO06 が同じ節を直接扱うのでここでは重複させない。★ E90 は errata 反映版に新 §4.1.6 を挿入するため、節番号 4.1.6 が OS 版（Use of Metadata）とerrata 反映版（Use of Relay State）で指す先が違う。IIP-SSO06 は節名も併記して OS 版を指しているので曖昧さはない。  【取り込み句 A: IdP MUST process the <AuthnRequest> as described in [SAMLCore]】取り込み範囲は、<AuthnRequest> の構文・検証・処理と、その処理が直接参照する共通規則の依存閉包である。節番号の短い列挙を正本とせず、以下の節ごとの対応表（§1.1・§1.3・§3.2.1・§3.3.2.2.1・§3.4.1〜§3.4.1.5.1・§4・§5・§6）を正本とする。独立した別 protocol である §3.5 Artifact Resolution・§3.6 Name Identifier Management 等は、Profile §4.1 または対応表が個別に取り込む規範句を除き対象外とする。§1.1 Notation（スキーマ文書が構文の正本）+ protocol / assertion schema: SP の AuthnRequest → .cg ／ IdP の Response → .dv ／ IdP の Assertion・AuthnStatement → .dw ／ プロキシ IdP の AuthnRequest（条件付き）→ .dx。★ role ごとに義務を分けている（variant に role フィールドがないため、1 義務に idp/sp を持たせると G2 で片方の role が他方の variant まで覆う必要があるように見える）。§1.3.1 文字列: 空白以外を 1 文字以上 → .dz ／ 完全バイナリ比較 → .ea ／ 大文字小文字無視・空白正規化・ロケール変換に依拠しない → .eb ／ 異なる符号化の比較は NFC → .ec ／ 外部データとの比較で XML 正規化を考慮 → .ed ／ ソート順に依拠しない → .ee。§1.3.2 URI: 空白以外を 1 文字以上かつ絶対 URI → .ef。§1.3.3 時刻: タイムゾーンなし UTC → .eg ／ ミリ秒より細かい分解能に依拠しない（SHOULD NOT）→ .eh ／ うるう秒を生成しない → .ei ／ E92 の合理的な clock skew（SHOULD）は IIP-G01 が MUST に強化して直接扱う。§1.3.4: 別オブジェクトへの重複割当をしない → .af（SP）/ .ao（IdP。<Response>/@ID と <Assertion>/@ID の両方）／ 宣言はちょうど 1 つ → .cc ／ 乱数使用時の衝突確率 ≤2^-128 → .cd ／ 同 ≤2^-160（SHOULD）→ .ce ／ PRNG の seed → .cf。§3.2.1: 要求 @ID と応答 @InResponseTo の一致 → .ap ／ @Destination の照合と破棄 → .ag ／ 拡張要素の名前空間修飾 → .ah ／ 署名の検証 → .ai ／ 署名不正時に内容へ依拠しない → .aj ／ 署名不正時のエラー応答（SHOULD）→ .ak ／ 署名者の同一性・妥当性の評価（SHOULD）→ .al ／ Consent 付き要求の署名（SHOULD）→ .am（SP）/ .gh（proxy IdP） ／ 不正な要求へ応答する場合の <StatusCode>（MUST）→ .an。§3.4.1 本体と §3.4.1.1 NameIDPolicy: ForceAuthn → IIP-IDP06 ／ IsPassive → IIP-IDP07 ／ AuthnRequest の署名または binding による認証・完全性保護（SHOULD）→ .fj（SP）/ .fx（proxy IdP） ／ NameIDPolicy の基本処理 → IIP-IDP10 ／ E14 の AllowCreate 新規則 → .fl〜.fp （.fl / .fm は『特定用途に使わない』条件を述語で明示）／ ACS 3 属性 → IIP-IDP12 ／ RequestedAuthnContext exact → IIP-IDP08、minimum / better / maximum → .ga / .gb / .gc、候補の優先順評価 → .gj ／ AttributeConsumingServiceIndex → IIP-IDP04.b ／ Subject → IIP-SSO01.c・.d と IIP-SSO07.b ／ ProviderName・Consent は処理規則の記述がなく IIP-SSO07.b の情報記録。§3.4.1.2 <Scoping>: RFC2119 の義務は『profiles specifying an active intermediary』の MAY のみ → 権限。§3.4.1.3 <IDPList>: <GetComplete> の解決結果に対する MUST → .av。§3.4.1.4 処理規則: 要求の仕様を満たす assertion か誤り応答か → IIP-IDP10.d ／ 認証できない・主体不明・ポリシーで拒否する場合の誤り応答 → IIP-IDP05.a と .d ／ <Subject> の strongly match → IIP-SSO07.b ／ 内容が空の場合の含意（AuthnStatement・AudienceRestriction）→ .l・.m。§3.4.1.5・§3.4.1.5.1 プロキシ: .aw〜.bj（すべて supports_authnrequest_proxying が条件）。★ E65 は ProxyCount=0 の旧『MUST NOT proxy / 二次 ProxyCountExceeded MUST』を置換したため、.aw は最上位 Responder の MUST、.ax は二次 ProxyCountExceeded の MAY として反映する。§4 バージョン処理: 双方が対応する最高版で要求を出す（SHOULD）→ .fg（SP）/ .ge（proxy IdP） ／ 応答元の能力が不明なら自身の最高版を仮定（SHOULD）→ .fh（SP）/ .gf（proxy IdP） ／ 未対応バージョンの assertion を発行しない → .ej ／ 未対応 major の assertion を処理しない → .ek ／ 自分が扱えない応答版に対応する要求を出さない → .el（SP）/ .gg（proxy IdP） ／ 未対応 major の要求を拒否 → .em ／ 要求より高い応答版を出さない → .en ／ 要求より低い major を出さない（VersionMismatch 報告を除く）→ .eo ／ 非互換時の最上位 VersionMismatch → .ep ／ V1 assertion を V2 応答に含めない → .eq。★ .eo の例外は二次コード RequestVersionTooHigh に限られる（VersionMismatch 一般ではない）。『minor が高い要求は処理しても拒否してもよい』（MAY）と『同じ major を共有する要求は同じ処理規則を持つ』（MUST。実装への義務ではなく仕様の性質の宣言）は義務を起こさない。§4.2 の namespace version 対応は仕様の書き手への規範なので義務を起こさない。§4.2.1 の将来拡張に備える SHOULD / mandatory semantics の未知拡張を拒否する SHOULD は、IIP-SSO07.b と IIP-EXT01 が内容種別ごとの処理結果を直接規定するため、そちらで判定し二重計上しない。§5 XML Signature profile: enveloped 署名 → .er ／ ★ RSA-SHA1 対応の旧 SHOULD は E81 が『任意の XML Signature アルゴリズムを MAY で使用できる』に置換したため義務を起こさない ／ 発行元以外から得る assertion の署名（SHOULD）→ .es ／ 発信者以外から届くメッセージの署名（SHOULD）→ .et（IdP の Response）/ .fj（SP の AuthnRequest）／ 署名対象ルートの ID → .eu ／ **単一 <ds:Reference> と same-document reference → .ev** ／ Exclusive C14N（SHOULD）→ .ew ／ 許可外 transform を含めない（SHOULD NOT）→ .ex ／ **許可外 transform を受理するなら内容が署名対象から除外されないことを保証 → .ey（SP）/ .fk（IdP）** ／ <ds:KeyInfo> は省略可（MAY）→ 権限 ／ E91 の <ds:Object> 送出 SHOULD NOT → .fs、verifier の拒否 SHOULD → .ft（SP）/ .fu（IdP） ／ §5.3 署名継承は小文字 should の記述なので advisory。§6 暗号化: E30 反映後も残る同じ位置での置換 → .ez（assertion）/ .fd（識別子）/ .fe（属性）／ §6.1 の @Type は .dm / .dn（§2.2.4 と同じ規則）。★ OS 版 §6.2 の『逆順で検証・復号 / assertion は署名後に暗号化 / 識別子・属性は暗号化後に外側署名』は、E43 が節全体を Key and Data Referencing Guidelines に置換し、さらに E93 が Encryption and Integrity Protection に置換した。したがって旧 §6.2 由来の .fa / .fb / .fc / .ff は Errata 反映後には存在せず削除する。E93 の現行 §6.2 にある CBC 暗号データの処理前完全性保護（SHOULD）→ .fw、Profile 4.1 への Response 署名追記（SHOULD）→ .fv。★ .ez / .fd / .fe は実際に送出された暗号化要素ごとの受動規則とし、観測がなければ satisfied_with_note。★ .er / .eu / .ev / .ew / .ex は**条件を持たない**。§5.4 の制約は『署名能力がある製品』ではなく実際に生成された各 XML 署名に適用される実行時条件なので、対象が送出した各署名を受動的に検査し、署名が 1 つも観測されなければ satisfied_with_note とする。★ .ey も**条件を持たない**。適用性はケース実行より先に評価されるため、『受理したかどうか』を条件にすると観測するためのケースが観測前にスキップされる循環になる。評価は transform ごとに『拒否した → satisfied ／ 受理したが内容を何も除外していない → satisfied ／ 内容を除外した署名を受理した → violated ／ 除外の有無を確認できない → not_verified』。原文が求めるのは『no content of the SAML message is excluded from the signature』であって、除外された内容を使わないことではない。受信するメッセージが role で違う（SP は <Response> / <Assertion>、IdP は <AuthnRequest>）ため role 別に義務を分けた。恒等な transform は受理・拒否の**二択**なので required variant にはせず、Suite 側 fixture の自己検証に置いて対象の verdict には影響させない（自己検証で見るのは fixture の署名が暗号学的に正しいことと恒等 transform が内容を除外しないことだけ。対象は許可外 transform の存在だけを理由に拒否しても適合なので、拒否理由は区別しない）。★ .fk は**常に署名済み AuthnRequest を送る**。対象が署名を必須にしているかどうかと、受信した署名を正しく検証する義務は別である。Suite SP の鍵を信頼させられない場合に限り not_verified とする。★ .ev / .ey は XML Signature Wrapping への直接の検出にあたる。  【取り込み句 B: SP MUST process the <Response> and enclosed <Assertion> as described in [SAMLCore]】取り込み範囲は、<Response> と内包 <Assertion> の構文・検証・処理、およびそれらが直接参照する共通規則の依存閉包である。以下の対応表（§2 全体・§3.2.2・§3.2.2.2、および共通規則として対応表に明記した §1・§4・§5・§6）を正本とする。§3.5 Artifact Resolution は別 protocol であり、Profile §4.1.4.4 が明示する 2 規範句（IIP-SSO01.u / .u1）を除いて、本取り込み句から再帰的に取り込まない。§3.2.2: @ID の一意性 → .ao ／ 要求への応答での @InResponseTo 必須・一致 → .ap ／ unsolicited の @InResponseTo 禁止 → .y、要求 ID を特定できない error path の禁止 → .gi ／ @Destination の照合と破棄 → .aq ／ 拡張要素の名前空間修飾 → .ah ／ 署名の検証 → .n ／ 署名不正時に内容へ依拠しない → .ar ／ 署名不正をエラーとして扱う（SHOULD）→ .as ／ 署名者の同一性・妥当性の評価（SHOULD）→ .at ／ Consent 付き応答の署名（SHOULD）→ .au。§3.2.2.2: 最上位 <StatusCode>/@Value が top-level リストの値 → .ch。★ 取り込み句 B の対象は §2 SAML Assertions 全体である（前版は §2.5 Conditions 中心に限定していた）。節ごとの対応は次のとおりで、義務を起こさない節にはその理由を書く。§2.1 スキーマ宣言: 規範句なし。§2.2.1 <BaseID> / §2.2.2 NameIDType: NameQualifier / SPNameQualifier の省略（SHOULD）→ .cy。§2.2.3 <NameID>: 規範句なし（Format ごとの規則は §8.3 で、IIP-SSO05 が扱う）。§2.2.4 <EncryptedID>: @Type の存在（SHOULD）→ .dm ／ @Type の値 → .dn ／ 暗号化内容の型（NameIDType **または AssertionType**、およびそれらの派生型）→ .do ／ **ciphertext の一意性 → .dp（この MUST は §2.2.4 にのみ置かれているので <EncryptedID> に限定）** ／ wrapped key の Recipient（SHOULD）→ .dq。§2.2.5 <Issuer>: RFC2119 句なし。Format 既定値 entity の帰結は .h / .i、修飾属性の省略は .cy。§2.3.1 <AssertionIDRef> / §2.3.2 <AssertionURIRef>: 規範句なし。Web Browser SSO は assertion を値で運ぶので参照形式は使わない。§2.3.3 <Assertion>: @ID の一意性 → .ao ／ 必須の @Version / @IssueInstant / <Issuer> → .dw（生成）・.cx（受信拒否）／ <Statement> の xsi:type → .ci ／ statement のない assertion は <Subject> を含む → .cj ／ 署名の検証 → .n ／ Assertion 署名不正時に依拠しない → .fy ／ Assertion issuer の評価（SHOULD）→ .fz ／ 『issuer は relying party にとって一義的であるべき』（SHOULD）は .i（MUST）に包含される。§2.3.4 <EncryptedAssertion>: .dm / .dn / .do / .dq。§2.4.1 <Subject>: 2 人以上の主体を識別しない（SHOULD NOT）→ .cz（スキーマの choice 制約だけでなく、<SubjectConfirmation> 内の識別子など意味上の複数主体を見る）。§2.4.1.1 <SubjectConfirmation>: E47 が追加した『Subject と異なる entity に使用させる場合はその entity を識別する（SHOULD）』→ .fr ／ bearer の要求は .j / .k（SAML2Prof 由来）。§2.4.1.2 <SubjectConfirmationData>: 拡張属性の名前空間 → .da ／ 妥当期間が assertion の内側（SHOULD。**上限・下限の両端**）→ .db ／ NotBefore < NotOnOrAfter → .dc ／ @Address の表記（SHOULD）→ .ds ／ bearer での Recipient / NotOnOrAfter / NotBefore 禁止 / InResponseTo → .k / .k1 / .k2。§2.4.1.3 KeyInfoConfirmationDataType: **義務を起こさない**。『確認方式が機構を定義する』は仕様の書き手への規範であって実装への義務ではなく、残りは holder-of-key 確認方式に固有で、Web Browser SSO は bearer を使う（.j）。ECP の holder-of-key は IIP-IDP13 が別に扱う。§2.5 Conditions: 下記のとおり。§2.6 <Advice>: 無視してよい任意コンテンツで、IIP-EXT01 が扱う。§2.7.1 <Statement>: xsi:type → .ci。§2.7.2 <AuthnStatement>: <Subject> の必須 → .dd ／ 必須の @AuthnInstant / <AuthnContext> → .dw・.cx ／ ★ SessionNotOnOrAfter の旧『セッションを終了扱いにする MUST』は E79 が上限の説明へ置換したため義務を起こさない。Web Browser SSO における具体的な処理は Profile 4.1.4.3 の .t（SHOULD）だけを適用する ／ SessionIndex の相関防止（SHOULD NOT）→ .de ／ 推奨される 2 方式（RECOMMENDED）→ .dy ／ 方式 (a) の値域の濃度（SHOULD）→ .df ／ 方式 (a) のランダム選択（SHOULD）→ .dg ／ SLO 対応時の SessionIndex 必須 → .l1。★ .df / .dg は原文の**方式 (a) の内部規則**なので述語 uses_small_integer_sessionindex を条件にしている。方式 (b)（assertion の @ID を使う）を採る実装には適用されない。§2.7.2.1 <SubjectLocality>: @Address の表記（SHOULD）→ .ds。§2.7.2.2 <AuthnContext>: 固有の RFC2119 句なし。要求側の扱いは IIP-IDP08 / IIP-SP06 / IIP-SP07。§2.7.3 <AttributeStatement>: <Subject> の必須 → .dh。§2.7.3.1 <Attribute>: @FriendlyName を同定の根拠にしない → IIP-SP11.a ／ 拡張属性の名前空間 → .di ／ 値のない属性は <AttributeValue> を省略 → .dj ／ **複数の離散値は個別の <AttributeValue> に（RECOMMENDED）→ .du** ／ 『複数 <AttributeValue> に xsi:type があるなら全て同一型』は小文字 must なので advisory ／ 『他の用途は semantics を定義しなければならない』は**仕様の書き手への規範**なので義務を起こさない。§2.7.3.1.1 <AttributeValue>: 空値 → .dk ／ null 値 → .dl。§2.7.3.2 <EncryptedAttribute>: .dm / .dn / .do / .dq。§2.7.4 <AuthzDecisionStatement> 以下: **義務を起こさない**。Web Browser SSO Profile は認可決定 statement を使わず、IdP が同梱した場合の扱いは IIP-SSO07.b（未対応の任意コンテンツ）が扱う。URI 正規化の SHOULD 群も認可決定の資源 URI に固有で、本 profile の対象外。§2.5.1 <Conditions>: <Condition> の xsi:type → .ck ／ <OneTimeUse> は 1 つまで → .cl ／ <ProxyRestriction> は 1 つまで → .cm。§2.5.1.1: Invalid / Indeterminate な assertion の拒否 → .co。§2.5.1.2: NotBefore < NotOnOrAfter → .cn ／ 期間の検証 → .p・.r。§2.5.1.4: 複数 <AudienceRestriction> の独立評価 → .cp ／ <Audience> に SP の entityID → .m。§2.5.1.5 <OneTimeUse>: 直ちに使う（SHOULD）→ .cq ／ 将来利用のため保持しない → .cr ／ 保持する実装は遵守する → .cs ／ 1 つまで → .cl。§2.5.1.6 <ProxyRestriction>: 制限に違反する assertion を発行しない → .ct ／ Count=0 → .cu ／ Count 減算 → .cv ／ <Audience> の範囲 → .cw ／ 1 つまで → .cm。§2.5 Conditions の期間・Audience の検証そのものは .r。§1.3.4 の細目のうち、プロキシ IdP が上流へ生成する AuthnRequest の @ID は .dr（条件付き）。
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.b</code> の詳細</summary>

- **必要な variant**:
  - `v-f7a689744f` 対象 SP が送る AuthnRequest に <saml:Issuer> がある
  - `v-e33a073689` その値が対象のメタデータの entityID と一致する
  - `v-46bae7630f` @Format が省略されている、または urn:oasis:names:tc:SAML:2.0:nameid-format:entity である
- **対照（negative control）**:
  - ★ 3 つの条件（存在・値・Format）を個別に見る。存在確認だけでは値の誤りを検出できない
  - ★ 対象が複数の entityID を持つ構成では、応答先の Test Peer に対応する entityID と比較する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.c</code> の詳細</summary>

- **必要な variant**:
  - `v-4105eeb89b` 対象が送る全 AuthnRequest を Transcript 全件で検査し、<Subject>/<SubjectConfirmation> がない
  - `v-741b5b172d` 対象が <Subject> を送らない構成でも満たされる（空虚に真）
- **対照（negative control）**:
  - ★ 受動的な常時チェック。全ケースに横断適用する
  - ★ <Subject> を送らない対象では違反しようがない。『<Subject> を送る対象』を preflight で申告させ、送らない場合は satisfied_with_note（観測機会なし）として記録する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.d</code> の詳細</summary>

- **必要な variant**:
  - `v-a2e391e5bf` 存在しない主体を指す <Subject> 付き AuthnRequest → エラー <Status> が返り、<Assertion> が 0 件
  - `v-b7f9698d10` 対照: 実在する主体を指す <Subject> → 成功応答が返る（すべてエラーにする実装を落とす）
  - `v-0e9562c953` エラー応答に <Assertion> も <EncryptedAssertion> も含まれていない
- **対照（negative control）**:
  - ★ 『エラーを返す』と『assertion を含めない』は別の観測。両方を確認する
  - ★ 二次 status code は指定されていないので、特定の値を判定条件にしない
  - ★ 対象が <Subject> 付き要求に非対応の場合はエラーを返すのが正しい挙動であり、この義務とは矛盾しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.e</code> の詳細</summary>

- **必要な variant**:
  - `v-974d8fa061` 未署名 AuthnRequest の内容をどう扱うか（信頼境界の設計）を申告で確認する
  - `v-6075f19b80` 未署名要求の ProviderName / Scoping / Conditions を無検証で反映していないことを申告で確認する
- **対照（negative control）**:
  - ★ 唯一の直接観測できる帰結は ACS の検証（IIP-SSO01.f ではなく IIP-IDP12.b）。そちらで自動判定する
  - ★ 『何を信頼したか』は内部処理なので、本義務自体は申告にとどめる。申告が IIP-IDP12.b の観測と矛盾したら INCONSISTENT
- **参照先仕様**: `SAML2Prof#4.1`
- **注記**: ACS の検証義務（署名の有無によらず要求元に紐づくことを確かめる）は IIP-IDP12.b が持つ。本義務はそれ以外の要求内容全般についての一般原則。
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.f</code> の詳細</summary>

- **必要な variant**:
  - `v-0f9e07af7a` エラーを誘発する要求（未知 Format / 認識できない主体 / IsPassive 不成立）→ 返る <Response> に <Assertion> が 0 件
  - `v-b4d3a9eccc` <saml:EncryptedAssertion> も 0 件であること
  - `v-995d4f2a76` 対照: 成功要求 → <Assertion> が 1 件以上（IIP-SSO01.g）
- **対照（negative control）**:
  - ★ 暗号化された assertion も『assertion』である。EncryptedAssertion の有無も見る
  - ★ 複数のエラー経路（Format / 主体 / IsPassive）で試す。1 経路だけでは実装差を拾えない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.g</code> の詳細</summary>

- **必要な variant**:
  - `v-17a960ef8c` SP-initiated 成功時 → <Assertion> または <EncryptedAssertion> が 1 件以上
  - `v-5f0e0c25fe` IdP-initiated（unsolicited）成功時 → 同上
- **対照（negative control）**:
  - ★ IIP-SSO01.f と対。エラー時 0 件・成功時 1 件以上の両方を見て初めて意味がある
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.h</code> の詳細</summary>

- **必要な variant**:
  - `v-bc85bdcb7a` <Response>/<Issuer> がある場合、値が対象の entityID と一致する
  - `v-c2ae1da6b3` その @Format が省略されているか entity である
  - `v-ab2843b49c` 対照: <Issuer> が省略されている応答を FAIL にしない（MAY である）
- **対照（negative control）**:
  - ★ 省略は許されている。存在を必須にすると適合実装を FAIL にする。存在が必須になるのは IIP-SSO01.h1 の条件を満たすときだけ
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.h1</code> の詳細</summary>

- **必要な variant**:
  - `v-c8a089dd65` Response 署名を有効にした構成 → <Response>/<Issuer> が存在する
  - `v-a14f6ce644` Assertion 暗号化を有効にした構成 → <Response>/<Issuer> が存在する
  - `v-e8b57e1b75` 対照: 署名も暗号化もない構成 → 省略されていてもよい
- **対照（negative control）**:
  - ★ IIP-SSO01.h（値の正しさ）と本義務（存在の必須化）は別の観測
  - ★ 署名・暗号化の有無を切り替えられないと対照が作れない。IIP-SSO04 / IIP-IDP09 の設定と連動させる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.i</code> の詳細</summary>

- **必要な variant**:
  - `v-6b1b4169f6` <Assertion>/<Issuer> が対象（応答してきた IdP）の entityID と一致する
  - `v-78be213446` その @Format が省略されているか entity である
  - `v-ef632d79d6` Proxy 構成: 上流 IdP の entityID ではなく、応答してきた対象自身の entityID であること
- **対照（negative control）**:
  - ★ <Response>/<Issuer> と <Assertion>/<Issuer> は別要素。両方を見る
  - ★ Proxy 変種が最も検出力の高いケース。IIP-SSO05.a6（NameQualifier は元の生成者）と混同しないこと。Issuer は応答者、NameQualifier は識別子の生成者である
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.i1</code> の詳細</summary>

- **必要な variant**:
  - `v-9d33f372d6` 複数 assertion を返す構成 → すべての <Assertion>/<Issuer> が同一値
  - `v-d6effae64f` assertion が 1 件の応答では空虚に真
- **対照（negative control）**:
  - ★ 複数 assertion を返させられない対象では観測機会がない。satisfied_with_note として記録し、『検証した』とは書かない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.i2</code> の詳細</summary>

- **必要な variant**:
  - `v-09097c01ce` 複数 assertion を返す構成 → 各 <Subject> が同一主体を指す
  - `v-24a17823f4` <Subject> の内容（<NameID> の Format や値）が異なること自体は許される。これを FAIL にしない
- **対照（negative control）**:
  - ★ 『同一主体』は値の一致ではない。Format 違いの識別子が同じ主体を指すことはある。判定は『Suite がログインさせた 1 人の主体に対応しているか』で行い、単純な文字列比較にしない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.j</code> の詳細</summary>

- **必要な variant**:
  - `v-3524370027` 返る assertion に Method=urn:oasis:names:tc:SAML:2.0:cm:bearer の <SubjectConfirmation> がある
  - `v-f56124b28d` 追加の <SubjectConfirmation> があってもよい（MAY）。これを FAIL にしない
  - `v-e2f4d62d2e` bearer 確認を持たない assertion が同梱されていても、本 profile の対象外として扱う（MAY）
- **対照（negative control）**:
  - ★ E26 の改訂前は『AuthnStatement を含む assertion のうち少なくとも 1 つ』だった。改訂後は『消費される assertion はすべて』に強まっている。errata 適用版で判定する
  - ★ holder-of-key など他の Method だけを返す実装を検出できること
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.k</code> の詳細</summary>

- **必要な variant**:
  - `v-72e7a03eef` Recipient が、実際に <Response> を配送した ACS URL と文字列一致する
  - `v-9e7e821246` ACS を切り替えた variant で Recipient も追随する（既定値に固定していない）
  - `v-bb1cb24674` NotOnOrAfter が存在し、応答時刻より後の時刻である
  - `v-13803cd5f3` Address 属性はあってもなくてもよい（MAY）。これを FAIL にしない
- **対照（negative control）**:
  - ★ Recipient を固定値で埋める実装は、ACS を 2 つ持つメタデータでないと検出できない
  - ★ NotOnOrAfter の『妥当な長さ』は原文に規定がない。値の大小を判定条件にしない（IIP-G01 と同じ理由）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.k1</code> の詳細</summary>

- **必要な variant**:
  - `v-7751b62698` bearer の <SubjectConfirmationData> に @NotBefore がない
  - `v-ebc4e3088f` <saml:Conditions>/@NotBefore は別要素であり、こちらにあってもよい。混同しない
- **対照（negative control）**:
  - ★ Conditions/@NotBefore と SubjectConfirmationData/@NotBefore を取り違えると、適合実装を FAIL にするか違反を見逃す。XPath を要素まで含めて固定する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.k2</code> の詳細</summary>

- **必要な variant**:
  - `v-af5b2e498b` SP-initiated → bearer <SubjectConfirmationData>/@InResponseTo が AuthnRequest/@ID と一致
  - `v-a50f05bd38` 同一セッションで 2 回続けて SSO → それぞれ対応する ID と一致する（前回の値を使い回していない）
  - `v-98bbc1cf82` <Response>/@InResponseTo も同じ値であること
- **対照（negative control）**:
  - ★ 2 回連続で試さないと『最後の ID を使い回す実装』を検出できない
  - ★ unsolicited の場合は IIP-SSO01.y（含めてはならない）に切り替わる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.l</code> の詳細</summary>

- **必要な variant**:
  - `v-2d736bff36` 成功応答に <saml:AuthnStatement> が 1 つ以上ある
  - `v-0b4035a68f` @AuthnInstant が実際の認証時刻を表している（要求より未来でない）
  - `v-4da2a08f7b` 複数の <AuthnStatement> があってもよい（MAY）。これを FAIL にしない
- **対照（negative control）**:
  - ★ AttributeStatement だけを返す実装を検出できること
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.l1</code> の詳細</summary>

- **必要な variant**:
  - `v-5653700812` SLO 対応 IdP → <AuthnStatement>/@SessionIndex が存在する
  - `v-943edfdf6b` 複数 <AuthnStatement> がある場合、すべてに存在する（E26 の『any』）
  - `v-918d13c0de` SessionIndex の値が SLO の LogoutRequest で実際に使える（IIP-IDP17 と接続する）
- **対照（negative control）**:
  - ★ E26 改訂前は『any such authentication statements』（bearer 確認を持つ assertion のもの）だったが、改訂後は『any authentication statements』に広がっている
  - ★ 条件が偽（SLO 非対応）なら NOT_APPLICABLE。ただし IIP-IDP17 が IdP に SLO を MUST としているため、IdP 対象で条件が偽になること自体が IIP-IDP17 の違反を示す
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.m</code> の詳細</summary>

- **必要な variant**:
  - `v-0b21e15caf` 各 bearer assertion に <saml:AudienceRestriction> があり、<saml:Audience> に Test Peer の entityID を含む
  - `v-0d45fb14e4` 他の <Audience> が併記されていてもよい（MAY）。これを FAIL にしない
  - `v-ac39e89644` 複数 assertion のとき、すべての bearer assertion に含まれる
- **対照（negative control）**:
  - ★ 『1 つの assertion にあれば足りる』と実装したケースは E26 改訂後の『Each』を検出できない
  - ★ Audience が entityID と完全一致することを見る。末尾スラッシュ差などの正規化を勝手に行わない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.n</code> の詳細</summary>

- **必要な variant**:
  - `v-01da95a4f4` <ds:SignatureValue> を改竄した <Response> → 拒否される
  - `v-eecfa66430` <ds:SignatureValue> を改竄した <Assertion> → 拒否される
  - `v-0123cb5760` 署名対象の内容だけを改竄し <ds:Signature> はそのままにした応答 → 拒否される
  - `v-160e99bf30` <ds:Reference>/@URI を別要素に差し替えた応答 → 拒否される
  - `v-6d505057a4` 対照: 正しい署名 → 受理される
- **対照（negative control）**:
  - ★ 訂正: 前版は『対象のメタデータにない鍵で署名した Response → 拒否される』を必須 variant にしていたが、IIP-SSO01.ai と同じ混同だった。それは**暗号学的な妥当性ではなく署名者・鍵の適切性評価**であり、原文では別の SHOULD（IIP-SSO01.at）である。暗号学的に正しい未知鍵の応答を受理しても本 MUST の違反ではない
  - ★ 本義務は『存在する署名を検証する』こと。『署名を必須にする』ことではない（そちらは IIP-SP13）
  - ★ Response 署名と Assertion 署名を別ケースにする。片方しか検証しない実装がある
  - ★ 対照が必須。すべて拒否する実装は本義務を『満たす』ように見える
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.o</code> の詳細</summary>

- **必要な variant**:
  - `v-bed5f0531b` Recipient を別の ACS URL に差し替えた Response → 拒否される
  - `v-1c6d7ced26` Recipient を対象の別エンティティの ACS URL にした Response → 拒否される
  - `v-734f97ea87` Recipient を空にした Response → 拒否される
  - `v-6510d28ad2` 対照: 正しい Recipient → 受理される
- **対照（negative control）**:
  - ★ ACS を 2 つ持つメタデータで、片方に配送しつつ Recipient をもう片方にする variant が最も強い
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.p</code> の詳細</summary>

- **必要な variant**:
  - `v-2f1575b89a` 申告されたスキュー許容幅 T を超えて過去の NotOnOrAfter → 拒否される
  - `v-94ede0c2ae` NotOnOrAfter を欠いた bearer <SubjectConfirmationData> → 拒否される（IIP-SSO01.k で必須）
  - `v-cde745c153` 対照: T の内側に収まる NotOnOrAfter → 受理される（IIP-G01 と同じ扱い）
- **対照（negative control）**:
  - ★ Samlier は絶対閾値を持たない。対象が申告した T の外側だけを判定に使う（IIP-G01 の決定に従う）
  - ★ T を申告できない場合は not_verified。設定できないこと自体は違反ではない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.q</code> の詳細</summary>

- **必要な variant**:
  - `v-510f61188f` 別の ID を入れた InResponseTo → 拒否される
  - `v-7e097566e9` SP-initiated なのに InResponseTo を欠いた応答 → 拒否される
  - `v-77339e68df` 前回のセッションの AuthnRequest ID を再利用した応答 → 拒否される
  - `v-eff25a3956` unsolicited 応答に InResponseTo を付けた → 拒否される
  - `v-a4484e6593` 対照: 正しい InResponseTo → 受理される。unsolicited で InResponseTo なし → 受理される
- **対照（negative control）**:
  - ★ この検査が SAML の代表的な脆弱性（応答の付け替え）に対応する。5 つの variant を対にする
  - ★ unsolicited を受理しない SP もある。preflight で申告させ、非対応なら unsolicited variant を not_verified にする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.r</code> の詳細</summary>

- **必要な variant**:
  - `v-f9a4a9e085` <saml:Conditions>/@NotOnOrAfter を過ぎた assertion → 拒否される
  - `v-33674bc28d` <saml:Conditions>/@NotBefore が未来の assertion → 拒否される
  - `v-c1f2233133` <AudienceRestriction> が対象の entityID を含まない assertion → 拒否される
  - `v-a73595473c` 対照: すべて妥当な assertion → 受理される
- **対照（negative control）**:
  - ★ 『その他の点』の外延は原文にない。SAML2Core の Conditions 処理規則に限定し、Samlier 独自の追加検査を義務にしない
  - ★ 各 variant を独立ケースにする。1 つでも見逃す実装を検出するため
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.r1</code> の詳細</summary>

- **必要な variant**:
  - `v-7b2ac684f1` 妥当な assertion と妥当でない assertion を 1 つの Response に同梱 → 妥当でないほうに依拠しない
  - `v-f35a8abe5c` 1 つの bearer <SubjectConfirmation> が妥当なら、その assertion は確認できる（E26 の前段）
- **対照（negative control）**:
  - ★ 『1 つでも妥当なら全部受理する』実装を検出するのが目的。妥当・不当を混在させた variant を作らないと検出できない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.s</code> の詳細</summary>

- **必要な variant**:
  - `v-aecca3ffcd` 妥当でない assertion を含む Response → その assertion を保持・利用していないことを観測する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。満たさなくても WARNING であって FAIL ではない
  - ★ 『破棄』は外部から直接は見えない。属性の反映有無など間接証拠で判定し、判定できなければ not_verified にする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.s1</code> の詳細</summary>

- **必要な variant**:
  - `v-48242fef7c` 妥当でない assertion だけを含む Response → セッションが確立しない
  - `v-ba90a062bc` 妥当な assertion と妥当でない assertion が同梱 → 妥当なほうだけでセッションが確立する
- **対照（negative control）**:
  - ★ IIP-SSO01.s（破棄）と本義務（利用しない）は別の観測。破棄していなくてもコンテキスト確立に使っていなければ本義務は満たされる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.t</code> の詳細</summary>

- **必要な variant**:
  - `v-88a87c7b87` 短い SessionNotOnOrAfter を設定 → その時刻以降、保護リソースへのアクセスで再認証が要求される
  - `v-f47b45ec4f` 複数 <AuthnStatement> がある場合、最も近い SessionNotOnOrAfter が使われる（E26。SHOULD）
- **対照（negative control）**:
  - ★ 待ち時間が要るため自動化しにくい。既定は申告とし、短い値を設定できる対象でのみ自動観測に格上げする
  - ★ SHOULD_CLASS。満たさなくても WARNING
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.u</code> の詳細</summary>

- **必要な variant**:
  - `v-5703750f2f` ArtifactResolve の往復が TLS 上で行われる
  - `v-3711172f64` 相互認証（クライアント証明書、またはメッセージ署名）が成立している
  - `v-cb793870b0` 対照: 相互認証のない解決要求 → 拒否される
- **対照（negative control）**:
  - ★ IIP-SSO02 / IIP-SSO03 が要求するのは Redirect と POST だけ。Artifact 非対応は違反ではない
  - ★ 『相互認証・完全性保護・機密性』は 3 条件。TLS だけでは相互認証を満たさない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.u1</code> の詳細</summary>

- **必要な variant**:
  - `v-2968948759` 別エンティティとして ArtifactResolve を送る → 拒否される
  - `v-5051eaf65b` 対照: 正当な SP として送る → <Response> が返る
- **対照（negative control）**:
  - ★ 2 つ目の Test Peer（secondary_peer）を別エンティティとして使う。IIP-SP05 の構成を流用できる
  - ★ artifact の one-time-use は SAML2Core §3.5.3 の独立した規則で、Profile §4.1.4.4 が明示する intended recipient 限定とは別である。本 CP で §3.5 全体を再帰的に取り込まないスコープ境界に従い、本義務の verdict 対象にしない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.v</code> の詳細</summary>

- **必要な variant**:
  - `v-9f6d31154e` POST 配送で <Assertion> 個別署名 → 適合
  - `v-c3bee03f33` POST 配送で <Response> 署名のみ → 適合（E26 が明示的に許している）
  - `v-f51ea98081` POST 配送で署名なし → 違反
  - `v-21cfcfde82` 複数 assertion のうち一部だけ署名 → 違反（『each』）
- **対照（negative control）**:
  - ★ 改訂前の『assertion が署名されていること』だけを見ると、Response 署名のみの適合実装を FAIL にする。E26 の 2 通りをどちらも通す判定にする
  - ★ IIP-SSO04（両方式に対応できること）は能力の義務、本義務は POST 使用時の禁止側。混同しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.w</code> の詳細</summary>

- **必要な variant**:
  - `v-f30d4c19c3` 同一の Assertion（同一 @ID）を 2 回 POST → 2 回目が拒否される
  - `v-cdb8272c1c` 別セッション・別ブラウザから同じ Assertion を POST → 拒否される
  - `v-da7a00ba92` 対照: @ID を変えた同等の Assertion → 受理される（ID ではなく内容で拒否していないことの確認）
  - `v-41df57f9cb` NotOnOrAfter を過ぎてからの再送 → 拒否される（IIP-SSO01.p でも拒否されるため両方の理由が成立する）
- **対照（negative control）**:
  - ★ 対照が必須。『2 回目は必ず拒否』だけを見ると、そもそも 1 回しか受け付けない実装と区別できない
  - ★ ★ ID を変えずに他の内容を変える variant を作らないこと。署名が壊れて別の理由で拒否され、検出力を失う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.x</code> の詳細</summary>

- **必要な variant**:
  - `v-0bce22eb7b` 対象が返す <Response> がすべて POST（または Artifact）で配送されている
  - `v-0ad23d56f2` メタデータの md:AssertionConsumerService に Redirect バインディングを載せても、Redirect では返さない
- **対照（negative control）**:
  - ★ IIP-SSO03（応答の POST 対応）が能力側、本義務が禁止側。両方ないと『Redirect でも返せてしまう』実装を素通しする
  - ★ 観測は Transcript 全件の受動チェックでよい
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.y</code> の詳細</summary>

- **必要な variant**:
  - `v-1d63df0d0f` IdP-initiated SSO → <Response>/@InResponseTo がない
  - `v-47f99ef92e` bearer <SubjectConfirmationData>/@InResponseTo もないことを記録する（原文は小文字 should なので advisory）
- **対照（negative control）**:
  - ★ 原文の 2 つ目は小文字の should であって RFC2119 キーワードではない。SubjectConfirmationData 側の有無は advisory として記録し、判定には使わない
  - ★ IIP-SSO01.k2（SP-initiated では一致させる）と対。両方揃って初めて意味を持つ
  - ★ unsolicited を発行しない IdP では条件が偽 → NOT_APPLICABLE。§4.1.5 の開始は MAY である
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.y1</code> の詳細</summary>

- **必要な variant**:
  - `v-fedf93fb6b` IdP-initiated SSO → isDefault="true" の ACS に配送される
  - `v-ea196dddc8` isDefault を別の ACS に変えて再取得 → 配送先が変わる
- **対照（negative control）**:
  - ★ SHOULD_CLASS。別の ACS に配送しても WARNING であって FAIL ではない
  - ★ ACS が 1 つしかないメタデータでは検出力がない
  - ★ 訂正: 原文は『If metadata ... is used』という条件付き SHOULD で、適用条件は 「unsolicited を発行する」∧「ACS 決定にメタデータを使う」の**連言**。前版は前者しか条件にしておらず、ACS をメタデータ以外で決める IdP にも SHOULD を課していた。述語 unsolicited_acs_from_metadata に連言として畳んだ
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.z</code> の詳細</summary>

- **必要な variant**:
  - `v-f82dcc4c00` IdP-initiated SSO を実行 → AuthnRequest なしでセッションが確立する（対応している場合）
  - `v-cc7b23da0f` 対応していない場合は NOT_SUPPORTED。適合違反ではない
- **対照（negative control）**:
  - ★ MAY_CLASS。unsolicited を発行しない IdP を FAIL にしてはならない
  - ★ 本義務の観測結果が、IIP-SSO01.y / .y1 の条件述語 supports_unsolicited_responses の材料になる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.aa</code> の詳細</summary>

- **必要な variant**:
  - `v-a68eb11ce9` unsolicited response の受理を無効化する手段が存在する（設定項目・ポリシー・ビルド構成のいずれか）
  - `v-0e7d0b2cee` 無効化した状態で IdP-initiated SSO → 受理されない
  - `v-e25ae5c02c` unsolicited を一切受け付けない実装（常時無効）→ E90 の安全目的を満たすので満たしていると扱う
- **対照（negative control）**:
  - ★ 判定対象は『無効化する手段を持つこと』そのもの（能力の SHOULD）。分岐は次の 3 つだけ:
  -    (1) 無効化する手段がない → violated（SHOULD_CLASS なので WARNING）
  -    (2) 手段はあるが権限・環境の都合でこの Run では切り替えられない → not_verified
  -    (3) unsolicited を常時受け付けない → satisfied（無効化された状態が既定というだけで、E90 の目的は満たされている）
  - ★ 訂正: 前版は configuration_failure_semantics を test_precondition にしていたため、『無効化機能がない』ケースまで not_verified に落ちていた。能力そのものが SHOULD なので normative_capability が正しい
  - ★ 訂正: 前版は『有効化すると受理される』を必須 variant に置いていたが、E90 は unsolicited を受理する義務を課していない。常に拒否する実装も適合であり、verdict 対象にできない
- **設定不能時の意味**: `normative_capability`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ab</code> の詳細</summary>

- **必要な variant**:
  - `v-9a46e9f36d` RelayState に javascript: スキームの URL を入れる → その URL に遷移しない
  - `v-1aaf705ee8` RelayState に data: スキームの URL を入れる → 同上
  - `v-f25d25e1f4` RelayState に file: スキームの URL を入れる → 同上
  - `v-2fff19334b` RelayState に vbscript: / about: スキームの URL を入れる → 同上
- **対照（negative control）**:
  - ★ SHOULD_CLASS。判定対象は**禁止スキームに遷移しないこと**だけ
  - ★ 訂正: 前版は『https / http の URL → 正常に遷移する』を必須 variant に置いていたが、E90 は http / https の RelayState を受理・遷移する義務を課していない。絶対 URL を一切受け付けない実装も、RelayState を不透明トークンとして扱う実装も適合する。http / https の遷移は **Suite 側の control fixture**（禁止スキームの判定が『何も遷移しない実装』で空虚に成立していないかの確認）としてのみ使い、対象の verdict には影響させない
  - ★ 条件 derives_url_from_relaystate が偽なら NOT_APPLICABLE（URL を導出しない実装には適用されない）
  - ★ 同じ E90 の『implementations MUST carefully sanitize the URL schemes』は [SAMLBind] への追記であり、IIP は [SAML2Bind] を errata 込みでは参照していないため、そちらの MUST は判定に使わない
  - ★ 『protection against unencoded executable content must be applied』は小文字の must で、SAML2Prof §1.2 Notation が RFC2119 キーワードを大文字と定めているため規範キーワードではない。XSS 保護の観測は advisory として記録する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ac</code> の詳細</summary>

- **必要な variant**:
  - `v-0f71710686` 復元に不要な情報の露出: 元 URL のフラグメント・セッションに無関係なクエリパラメータが RelayState に含まれている → violated 候補
  - `v-9b07f0cd24` 識別子の露出: 元 URL に置いたメールアドレス等が、遷移の復元に不要であるのに RelayState に含まれている → violated 候補
  - `v-8a90f6817c` 対照: RelayState に元リソースのパスだけが入っている → **これだけでは violated にしない**（状態保持方式によっては復元に必要な最小限でありうる）
  - `v-f3c4874271` 対照: RelayState が不透明トークン → satisfied（ただし不透明化は義務ではない）
- **対照（negative control）**:
  - ★ SHOULD_CLASS。判定は三分岐にする:
  -    (1) 復元に不要な情報まで露出している → violated（SHOULD_CLASS なので WARNING）
  -    (2) 必要最小限かどうか判断できない → **not_verified**
  -    (3) 単に元 URL の文字列が含まれる → **それだけでは violated にしない**
  - ★ 訂正: 前版は『元 URL・クエリ・識別子がそのまま現れないこと』を要求していたが、原文は『as little ... as possible』であって完全非出現までは求めていない。状態保持方式によっては必要最小限のパスや識別子を含めることもありうるので、そのままでは適合 SP に WARNING を出す
  - ★ (1) の判定には『何が復元に必要か』の基準が要る。preflight で対象の状態保持方式（RelayState に何を入れているか）を申告させ、申告と観測が矛盾したら INCONSISTENT。申告がなければ (2) の not_verified に落とす
  - ★ RelayState は SAML バインディングで 80 バイト以内と定められている。長さだけを見ると『短いから露出していない』と誤判定する。値そのものを見る
  - ★ 『RelayState は不透明トークンであるべき』も原文にない要求なので variant から削除した
  - ★ 訂正 2: 前版は『プライバシー保護不要』の申告だけで satisfied_with_note にしていた。SHOULD を自己申告で通過させる経路になるため、原文の unless 節を**明示的な条件述語**relaystate_privacy_required に移した。除外は理由付きの申告でしか偽にできず、その Run は結果の最上位に『申告のみの除外』として現れる（docs/03 §申告のみの除外）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ad</code> の詳細</summary>

- **必要な variant**:
  - `v-35202e1b24` 要求ステップで実際に行われた HTTP 交換（AuthnRequest の配送）が TLS 上である
  - `v-96501619b1` 応答ステップで実際に行われた HTTP 交換（<Response> の配送）が TLS 上である
- **対照（negative control）**:
  - ★ SHOULD_CLASS（RECOMMENDED）。TLS でなければ violated → WARNING
  - ★ 訂正 1: 前版は『メタデータに載る全 SSO / ACS エンドポイントが https であること』を必須 variant にしていたが、原文は『このステップの HTTP 交換』についての推奨であって、使われなかったエンドポイントがメタデータに載っていることを禁じていない。**判定対象は Transcript に現れた実際の交換だけ**にする
  - ★ 訂正 2: 前版は『非本番構成なら not_verified』としていたが、原文にそのような適用除外はない。Samlier 独自の免除を作らない（IIP-G01 と同じ理由）
  - ★ 原文が挙げるのは SSL 3.0 / TLS 1.0 だが、いずれも現在は危殆化している。『TLS を使うこと』のみを判定に使い、版の妥当性は IIP-ALG07（RFC7457 と現行ベストプラクティス）に委ねる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ae</code> の詳細</summary>

- **必要な variant**:
  - `v-030129ce1b` 【前提】既存 IdP セッション（Cookie）・クライアント証明書・Kerberos / 統合認証・IP ベース認証など、非対話の身元確立手段をすべて無効化した構成にする
  - `v-60181ec7f5` 上記の前提下で SSO を開始 → 認証が要求されるか、エラー <Status> が返る（成功 assertion が返らない）
  - `v-f905f4208d` 対照: 前提を満たしたうえで正しく認証 → 成功応答が返る（常にエラーを返す実装を落とす）
  - `v-be3b21e525` 対照: 身元を確立できないとき → エラー <Status> が返り、assertion が含まれない（IIP-SSO01.f）
- **対照（negative control）**:
  - ★ 訂正: 前版は『画面上の認証操作がない成功応答』を違反の証拠にしていたが、IdP は既存セッション・クライアント証明書・Kerberos / 統合認証などの非対話手段でも身元を確立できる。ForceAuthn のない通常要求では既存セッションの利用も許されている。**BROWSER 観測だけで『身元を確立していない』とは結論できず、適合 IdP を FAIL にする**
  - ★ したがって判定には『ambient authentication を確実に排除した構成』が要る。testability を CONFIG とし、前提を作れない場合は not_verified(ambient_auth_not_excludable)。対象の不適合ではない
  - ★ 訂正: 前版は variant に『前提を作れない場合の代替: 申告で確認する』を置きつつ、control では『前提を作れない場合は not_verified』としており両立していなかった。**申告だけで satisfied にはしない**。申告は evidence / advisory としてのみ記録し、outcome は not_verified のままにする（誤った MUST PASS を出さないため）
  - ★ ForceAuthn による再確立の義務は IIP-IDP06.a、IsPassive での制約は IIP-IDP07.a。本義務はそれらの前提となる一般規則
  - ★ この義務だけを見て PASS にすると、任意の主体に assertion を発行する実装を素通しする。IIP-SSO01.f / .l と組にする
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.af</code> の詳細</summary>

- **必要な variant**:
  - `v-a593295a2d` 対象が送る全 AuthnRequest の @ID が xs:ID の字句規則に適合する（先頭が数字でない等）
  - `v-d8a8ab311f` 連続する複数回の SSO で @ID が毎回異なる（別の要求という別オブジェクトに同じ値を割り当てていない）
  - `v-34b157d53c` 並行する複数セッションでも @ID が衝突しない
  - `v-48ff594428` AuthnRequest の @ID が、同じ対象が発行する他のオブジェクトの @ID と衝突しない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。全ケースに横断適用する
  - ★ 本義務は『別のデータオブジェクトに同じ識別子を割り当てない』（negligible probability）まで。確率そのものの評価は IIP-SSO01.cd / .ce、PRNG の seed は .cf、『1 オブジェクトの宣言はちょうど 1 つ』は .cc に分けた。いずれも BROWSER / AUTOMATED 観測では証明できないため testability が違う
  - ★ 連番であること自体は違反ではない（原文は一意性しか要求していない）。advisory に記録する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ag</code> の詳細</summary>

- **必要な variant**:
  - `v-c6c6a72f02` @Destination を別 IdP の SSO エンドポイントにした AuthnRequest → 破棄される
  - `v-167446311a` @Destination を対象の別エンドポイント（SLO 等）にした AuthnRequest → 破棄される
  - `v-64abf588f9` @Destination のホストだけを変えた AuthnRequest → 破棄される
  - `v-da31ee838d` 対照: 正しい @Destination → 受理される
  - `v-33dc9f6043` 対照: @Destination を省略 → 受理される（Optional なので省略自体は違反ではない）
- **対照（negative control）**:
  - ★ 悪意ある転送（malicious forwarding）への対策。対照がないと『常に破棄する実装』を PASS にする
  - ★ 省略時に破棄する実装は誤り。省略ケースを必ず対にする
  - ★ HTTP-Redirect バインディングでは署名対象に @Destination が含まれる。改竄すると署名不正でも落ちるので、未署名要求でも試して理由を切り分ける
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ah</code> の詳細</summary>

- **必要な variant**:
  - `v-c607e7d5c3` 対象が送る <samlp:Extensions> の子要素が SAML 定義名前空間に属さない
  - `v-6688f4a187` 拡張を送らない対象では空虚に真
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-EXT01（拡張の消費）とは方向が逆で、こちらは生成側の規則
  - ★ 拡張を送らない対象では観測機会がない。satisfied_with_note とし『検証した』とは書かない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ai</code> の詳細</summary>

- **必要な variant**:
  - `v-5dc6210243` <ds:SignatureValue> を改竄した AuthnRequest → 受理されない
  - `v-c6f7d7ab51` 署名対象の内容（ACS URL 等）だけを改竄し <ds:Signature> はそのままにした AuthnRequest → 受理されない
  - `v-5d2ea438af` <ds:Reference>/@URI を別要素に差し替えた AuthnRequest → 受理されない
  - `v-80c2ce5b57` 対照: 正しい署名の AuthnRequest → 受理される
- **対照（negative control）**:
  - ★ 訂正: 前版は『対象のメタデータにない鍵で署名した要求 → 受理されない』を必須 variant にしていたが、それは**暗号学的な妥当性ではなく署名者・鍵の信頼性評価**であり、原文では別の SHOULD（IIP-SSO01.al）である。暗号学的に正しい未知鍵の要求を受理しても本 MUST の違反ではない
  - ★ IIP-SSO01.n（SP 側の応答署名検証）と対になる IdP 側の義務。IIP にはこれを扱う要件が他にない
  - ★ 未署名要求を拒否する義務ではない（WantAuthnRequestsSigned は MAY）。『署名があるなら検証する』ことだけを見る
  - ★ 本義務の対象は XML Signature（<ds:Signature>）に限る。HTTP-Redirect バインディングの DEFLATE + クエリ文字列署名は [SAML2Bind] 側の別機構であり、IIP-SSO01 が取り込む [SAML2Core] の規範句ではない。Redirect の署名検証の観測は advisory として記録する（実装上の注意は docs/02 §3.5）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.aj</code> の詳細</summary>

- **必要な variant**:
  - `v-cf7be7770f` 署名不正の AuthnRequest に載せた ACS URL / ProviderName / NameIDPolicy が結果に反映されない
  - `v-b9ccd155e7` 署名不正の AuthnRequest に対して assertion が発行されない
- **対照（negative control）**:
  - ★ IIP-SSO01.ai（検証すること）と本義務（結果に反映しないこと）は別の観測。検証しても内容を使ってしまう実装がある
  - ★ IIP-SSO01.e（未署名要求の情報を advisory を超えて信頼しない）と区別する。あちらは署名がない場合、こちらは署名があって不正な場合
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ak</code> の詳細</summary>

- **必要な variant**:
  - `v-c60dace8a3` 署名不正の AuthnRequest → エラー <Status> を持つ <Response> が返る
  - `v-57392bac5a` 対照: 無応答（タイムアウト）や 500 エラーは SHOULD を満たさない
- **対照（negative control）**:
  - ★ SHOULD_CLASS。エラーを返さなくても WARNING であって FAIL ではない
  - ★ 応答先の決定は IIP-IDP05（acceptable location）に従う。署名不正の要求に載っていた ACS URL へ返してはならない（IIP-IDP12.b）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.al</code> の詳細</summary>

- **必要な variant**:
  - `v-785c9e9a91` 別エンティティの鍵で正しく署名された AuthnRequest（署名自体は妥当）→ Issuer と署名者の不一致を検出する
  - `v-4b22d33791` 署名検証に使う鍵を Issuer のメタデータに限定していることを申告で確認する
- **対照（negative control）**:
  - ★ 『署名が数学的に妥当』と『正しい署名者である』は別。任意の信頼鍵で検証する実装は前者しか見ていない
  - ★ secondary_peer の鍵で署名した要求を使うと自動観測できる。その構成が作れない場合のみ申告に落とす
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.am</code> の詳細</summary>

- **必要な variant**:
  - `v-5cb1eccec9` 対象が @Consent に同意取得を示す値を入れた AuthnRequest を送る場合、その要求に署名がある
  - `v-e600bdc393` @Consent を送らない、または unspecified を送る対象では空虚に真
- **対照（negative control）**:
  - ★ SHOULD_CLASS。条件は原文の中にある（@Consent が同意取得を示す場合）ので述語を作らない
  - ★ 観測機会がない場合は satisfied_with_note とし『検証した』とは書かない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.an</code> の詳細</summary>

- **必要な variant**:
  - `v-d36505fa31` スキーマ違反の AuthnRequest（必須属性欠落）→ 応答するなら SAML の <Response> で、<StatusCode>/@Value=urn:oasis:names:tc:SAML:2.0:status:Requester
  - `v-6bffd4888e` Version が 2.0 でない AuthnRequest → 同上
  - `v-98f5dcb272` 対照: 同じ不正要求に status:Responder を返す → 違反
  - `v-c96194f147` 対照: 応答しない（HTTP エラーで打ち切る）ことは本義務の違反ではない（原文の『if it responds』）
- **対照（negative control）**:
  - ★ 原文は『応答するなら』という条件付き。無応答を FAIL にしない
  - ★ HTML のエラーページを返すのは SAML 応答ではない。応答した場合はそれが SAML の <Response> かを見る
  - ★ IIP-IDP05（エラー時に適切な status code を持つ <Response> を発行する）と重なるが、あちらは IIP の独立要件で名宛人が IdP、こちらは取り込まれた Core の一般規則。ケース設計では共有 fixture を使い、判定は義務ごとに行う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ao</code> の詳細</summary>

- **必要な variant**:
  - `v-a758ac1f21` 対象が送る全 <Response>/@ID が xs:ID の字句規則に適合する
  - `v-bb268f4cd7` 対象が送る全 <Assertion>/@ID が xs:ID の字句規則に適合する
  - `v-95fe3ba476` 連続する複数回の SSO で <Response>/@ID と <Assertion>/@ID がそれぞれ毎回異なる
  - `v-46bcb641e6` <Response>/@ID と、その中の <Assertion>/@ID が同じ値になっていない（別オブジェクトへの重複割当）
  - `v-90626e5e0f` 1 つの <Response> に複数 <Assertion> があるとき、それぞれの @ID が異なる
  - `v-5bf8052484` 並行する複数セッションでも衝突しない
- **対照（negative control）**:
  - ★ 受動的な常時チェック
  - ★ 訂正: 前版は『Assertion/@ID の一意性は IIP-SSO01.w が扱う』と書いていたが不正確だった。IIP-SSO01.w は **SP 側の replay 検出**であって、**IdP が SAML2Core 1.3.4 に従って Assertion ID を生成する義務**の代用にはならない。本義務に <Assertion>/@ID を含めた
  - ★ 確率の評価は IIP-SSO01.cd / .ce、PRNG の seed は .cf、『1 オブジェクトの宣言はちょうど 1 つ』は .cc
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ap</code> の詳細</summary>

- **必要な variant**:
  - `v-96ea9260e5` SP-initiated → <Response>/@InResponseTo が AuthnRequest/@ID と一致する
  - `v-c10c515c8f` 同一セッションで 2 回続けて SSO → それぞれ対応する ID と一致する（前回の値を使い回していない）
  - `v-4fb44935ca` SP-initiated なのに @InResponseTo を欠いた応答を返していない
- **対照（negative control）**:
  - ★ IIP-SSO01.k2 は bearer <SubjectConfirmationData>/@InResponseTo、本義務は <Response> 要素の属性。別の場所なので両方を見る
  - ★ unsolicited の場合の禁止側は IIP-SSO01.y
  - ★ 2 回連続で試さないと『最後の ID を使い回す実装』を検出できない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.aq</code> の詳細</summary>

- **必要な variant**:
  - `v-f0b4c1516a` @Destination を対象の別 ACS にした <Response> → 破棄される
  - `v-65caf6b79d` @Destination を別エンティティの ACS にした <Response> → 破棄される
  - `v-1e4002fec2` @Destination のホストだけを変えた <Response> → 破棄される
  - `v-bb4a04aa9d` 対照: 正しい @Destination → 受理される
  - `v-fdffaeb10e` 対照: @Destination を省略 → 受理される（Optional なので省略自体は違反ではない）
- **対照（negative control）**:
  - ★ 悪意ある転送への対策。IIP-SSO01.o（Recipient 照合）と似ているが別要素・別規則。Recipient は bearer <SubjectConfirmationData>、Destination は <Response> のルート属性
  - ★ 省略時に破棄する実装は誤り。省略ケースを必ず対にする
  - ★ 署名済み <Response> では @Destination も署名対象。改竄すると署名不正でも落ちるので、署名の有無を切り替えて理由を切り分ける
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ar</code> の詳細</summary>

- **必要な variant**:
  - `v-a9d488b621` 署名不正の <Response> でセッションが確立しない
  - `v-1d21298531` 署名不正の <Response> に載せた属性が対象アプリに反映されない
- **対照（negative control）**:
  - ★ IIP-SSO01.n（検証すること）と本義務（結果に反映しないこと）は別の観測
  - ★ 属性の反映は読み戻し面が要る（IIP-G02.b と同じ経路を使う）。読み戻せない場合はセッション確立の有無だけで判定し、その旨を記録する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.as</code> の詳細</summary>

- **必要な variant**:
  - `v-9441a03a47` 署名不正の <Response> → セキュリティコンテキストが成立しない
  - `v-1c025763bf` その事象がエラーとして扱われている（利用者への提示・監査ログ・エラーページのいずれかで確認できる）
- **対照（negative control）**:
  - ★ SHOULD_CLASS
  - ★ 訂正: 前版は『利用者にエラーが提示される』ことを満足条件にしていたが、原文が求めるのは『エラーとして扱う』ことであって UI 表示ではない。セッションを成立させず内部エラーとして処理・記録する実装も適合する。UI 表示を必須にすると原文より強い WARNING 条件になる
  - ★ 内部処理を観測できない場合は not_verified。『セキュリティコンテキストが成立しない』ことだけは自動観測できるので、そこまでは判定に使う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.at</code> の詳細</summary>

- **必要な variant**:
  - `v-26e89e1628` secondary_peer（別 IdP）の鍵で正しく署名された <Response>（署名自体は妥当）→ Issuer と署名者の不一致を検出する
  - `v-41e3d0f3a1` 署名検証鍵を Issuer のメタデータに限定していることを申告で確認する
- **対照（negative control）**:
  - ★ 『署名が数学的に妥当』と『正しい署名者である』は別。信頼ストア内の任意の鍵で検証する実装は前者しか見ていない
  - ★ secondary_peer を使えば自動観測できる。IIP-SP05 の構成を流用する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.au</code> の詳細</summary>

- **必要な variant**:
  - `v-56f286442f` 対象が @Consent に同意取得を示す値を入れた <Response> を送る場合、<samlp:Response> 要素そのものに <ds:Signature> がある
  - `v-135e8abd4f` @Consent を送らない、または unspecified を送る対象では空虚に真
- **対照（negative control）**:
  - ★ SHOULD_CLASS。条件は原文の中にあるので述語を作らない
  - ★ 訂正: 前版は『応答（または assertion）に署名がある』を満足条件にしていたが、原文が求めるのは **<Response> の署名**である。assertion だけを署名しても <Response>/@Consent は保護されない。Response レベルの署名を判定条件にする
  - ★ したがって IIP-SSO01.v（POST 時に各 assertion が署名で保護される）を満たしていても本 SHOULD を満たすとは限らない。両者を混同しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.av</code> の詳細</summary>

- **必要な variant**:
  - `v-7040c02ba2` 対象が発行した <GetComplete> の URI を取得 → ルート要素が <samlp:IDPList> である
  - `v-02d9b080a5` その <IDPList> が <samlp:GetComplete> を含まない（再帰しない）
  - `v-aba70ca936` 取得した <IDPList> が 1 つ以上の <samlp:IDPEntry> を持つ（スキーマ上必須）
- **対照（negative control）**:
  - ★ <GetComplete> を発行しない対象では条件が偽 → NOT_APPLICABLE
  - ★ role には idp も含む。プロキシ IdP は新 <AuthnRequest> に <IDPList>/<GetComplete> を載せうる（IIP-SSO01.bc）
  - ★ 訂正: 前版は到達不能を一律 not_verified にしていたが、それでは壊れた URI を隠してしまう。次のように切り分ける:
  -    (1) Suite に外向き通信がない／プロキシ制限で到達できない → not_verified(suite_egress_restricted)
  -    (2) Suite は他ホストへ到達できるのに当該 URI が 404・接続拒否・TLS 失敗 → violated（『Retrieving the resource ... MUST result in an XML instance』を満たしていない）
  -    (3) 取得できたが XML でない・ルートが <IDPList> でない・<GetComplete> を含む → violated
  - ★ (1) と (2) を区別するため、preflight で既知の到達可能ホストへの疎通を先に確認しておく
  - ★ 取得は Suite の outbox 経由で行い、リダイレクト追従とサイズ上限を Runner 側で制限する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.aw</code> の詳細</summary>

- **必要な variant**:
  - `v-99d255118e` <Scoping ProxyCount="0"> かつ対象が直接認証できない主体 → <Response> が返り、最上位 <StatusCode>/@Value が urn:oasis:names:tc:SAML:2.0:status:Responder
  - `v-e98274e3fa` 同じケースで対象が上流 IdP へ AuthnRequest を送らず、成功 assertion を返さない
  - `v-085dcda419` 対照: ProxyCount=0 でも対象が直接認証できる主体 → 成功応答でよい
  - `v-ef43ff5f02` 対照: ProxyCount=1 → プロキシしてよい
- **対照（negative control）**:
  - ★ Errata E65 は旧文全体を置換した。旧『ProxyCount=0 を MUST NOT proxy』を独立義務として残さず、直接認証できない場合の必須エラー応答として判定する
  - ★ 二次 ProxyCountExceeded は E65 で MAY に緩和されたため、本義務の満足条件にしない
  - ★ 『直接認証できない主体』を作れることがテスト前提。作れない場合は not_verified
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ax</code> の詳細</summary>

- **必要な variant**:
  - `v-f9186f55be` IIP-SSO01.aw のエラー Response に二次 <StatusCode> があれば、その値を記録する
  - `v-59bfc8866d` ProxyCountExceeded がなくても違反にしない
- **対照（negative control）**:
  - ★ MAY_CLASS。Errata E65 は旧 MUST を MAY に緩和した。二次コードを省略する適合実装を FAIL にしてはならない
  - ★ 最上位 Responder は IIP-SSO01.aw の MUST として別に判定する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ay</code> の詳細</summary>

- **必要な variant**:
  - `v-d15cb52010` 元要求の <RequestedAuthnContext> が、上流への新要求にも同等以上の厳しさで現れる
  - `v-dc3a6c1617` 元要求の ForceAuthn=true が、新要求にも true で現れる
  - `v-e07879b7d5` 元要求の IsPassive=true が、新要求にも true で現れる
  - `v-0c3777b595` 対照: <NameIDPolicy> だけは自由に指定してよい（原文が明示的に除外している）
- **対照（negative control）**:
  - ★ <NameIDPolicy> を除外に入れないと、適合 Proxy を FAIL にする。原文の『the proxying provider is free to specify whatever <NameIDPolicy> it wishes』
  - ★ 『同等またはより厳しい』の判定基準を要素ごとに固定する。AuthnContext は比較方式（exact / minimum 等）まで含めて比べる
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.az</code> の詳細</summary>

- **必要な variant**:
  - `v-15badea891` 非 SAML の上流（OIDC / LDAP / 独自）を使う構成で、IsPassive=true が上流にどう伝わるかを申告で確認する
  - `v-815e5376ae` ForceAuthn=true についても同じ手段が用意されていることを申告で確認する
- **対照（negative control）**:
  - ★ 上流が非 SAML の場合、Samlier は上流を演じられないので観測できない。申告にとどめる
  - ★ 訂正: 前版は『非 SAML の上流を使わない構成では空虚に真』を variant に置いていたが、条件が偽なら NOT_APPLICABLE であって『満たした』ではない。削除した
  - ★ 上流が SAML の場合は条件が偽 → NOT_APPLICABLE。同等の内容は IIP-SSO01.ay が自動で見る
  - ★ 条件述語 proxies_to_non_saml_provider は CLASSIFICATION_BASED。上流の種別はプロトコル面に現れないので、理由付きの除外申告でしか偽にできない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ba</code> の詳細</summary>

- **必要な variant**:
  - `v-6b6ba2c738` 元要求 ProxyCount=3 → 上流への新要求の ProxyCount が 2 以下
  - `v-878aeda3f4` 元要求 ProxyCount=1 → 新要求の ProxyCount が 0
  - `v-0f8a67acc5` 対照: 新要求の ProxyCount が元と同じ、または大きい実装を検出できる
- **対照（negative control）**:
  - ★ 『at most one less』なので 2 以上減らすのも適合。『ちょうど 1 減』を要求すると適合 Proxy を FAIL にする
  - ★ ProxyCount は <Scoping> の属性。要素として探すと見つからない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bb</code> の詳細</summary>

- **必要な variant**:
  - `v-b4054fc792` ProxyCount を省略した AuthnRequest → 上流への新要求に ProxyCount がある
- **対照（negative control）**:
  - ★ SHOULD_CLASS。値の大小は原文が定めていないので判定に使わない（存在の有無だけ）
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bc</code> の詳細</summary>

- **必要な variant**:
  - `v-5247177b9d` <IDPList> を含む AuthnRequest → 上流への新要求にも <IDPList> がある
  - `v-8647bbbfa1` 対照: <IDPList> を含まない AuthnRequest → 新要求に <IDPList> がなくてよい
- **対照（negative control）**:
  - ★ 対照がないと『常に <IDPList> を付ける実装』と区別できない（それ自体は違反ではないが、元要求に依存していることを示せない）
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bd</code> の詳細</summary>

- **必要な variant**:
  - `v-837732a899` 3 件の <IDPEntry>（うち 1 件は上流 Samlier-IdP、2 件は到達不能な entityID）を含む AuthnRequest → 上流への新要求の <IDPList> に 3 件すべてが残っている
  - `v-e49e2e65b8` 追加された <IDPEntry> があれば末尾に置かれている（MAY。順序は原文が『to the end』と定める）
  - `v-9e53347bb4` 対照: 到達不能なエントリを『整理』して削る実装を検出できる
- **対照（negative control）**:
  - ★ 検出力の要: 到達不能・未知の <IDPEntry> を混ぜないと、削除する実装を検出できない
  - ★ 末尾への追加は許されている。追加があること自体を FAIL にしない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.be</code> の詳細</summary>

- **必要な variant**:
  - `v-a23bfc5a57` 元要求で Format=persistent を指定 → プロキシ経由で返る assertion の <NameID>/@Format が persistent
  - `v-6caa609e06` 元要求で SPNameQualifier を指定 → 返る <NameID>/@SPNameQualifier が一致する
  - `v-92645e8f5e` 上流が別 Format を返しても、対象は元要求の Format に合わせる
- **対照（negative control）**:
  - ★ IIP-IDP10.d（非プロキシ時の NameIDPolicy 遵守）のプロキシ版。上流の応答をそのまま素通しする実装を検出するのが目的
  - ★ 上流 Samlier-IdP に意図的に別 Format を返させる variant が最も強い
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bf</code> の詳細</summary>

- **必要な variant**:
  - `v-5f7676ec82` プロキシ経由の assertion に <saml:AuthenticatingAuthority> がある
  - `v-15ffa26194` その値が上流 Samlier-IdP の entityID と一致する
  - `v-07c556e852` 対照: プロキシしなかった場合（直接認証）は <AuthenticatingAuthority> がなくてよい
- **対照（negative control）**:
  - ★ この要素の有無が、Suite から見た『プロキシが起きた』ことの証拠になる。述語 supports_authnrequest_proxying の観測材料でもある
  - ★ 値が対象自身の entityID になっている実装を検出できること
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bg</code> の詳細</summary>

- **必要な variant**:
  - `v-17de7d3356` 上流 Samlier-IdP が <AuthenticatingAuthority> を 1 件含む assertion を返す → 対象の新 assertion にその 1 件が残り、対象が付ける要素はその後ろにある
  - `v-c199b9db2b` 順序が保たれている（連鎖の順序が読み取れる）
- **対照（negative control）**:
  - ★ SHOULD_CLASS
  - ★ 順序まで見ないと『集合として含む』実装と区別できない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bh</code> の詳細</summary>

- **必要な variant**:
  - `v-a076811b2b` 非 SAML の上流を使う構成で、<AuthenticatingAuthority> に置く値の生成方法を申告で確認する
  - `v-8f3a3e6a0d` その値が上流 provider ごとに区別されることを申告で確認する
- **対照（negative control）**:
  - ★ 上流が非 SAML の場合は Samlier が上流を演じられないので申告にとどめる
  - ★ 訂正: 『非 SAML の上流を使わない構成では空虚に真』の variant を削除した。条件が偽なら NOT_APPLICABLE であって満足ではない
  - ★ 値の一貫性は IIP-SSO01.bi、衝突回避は IIP-SSO01.bj
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bi</code> の詳細</summary>

- **必要な variant**:
  - `v-655b10f8a1` 非 SAML 上流を使う構成で 2 回 SSO → <AuthenticatingAuthority> の値が同じであることを観測または申告で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS
  - ★ 2 回の観測で一致しても『時間的な一貫性』の証明にはならない。明白な不一致のみ自動検出し、残りは申告
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bj</code> の詳細</summary>

- **必要な variant**:
  - `v-39b560a2ac` 生成値が既知の SAML entityID（Test Peer の entityID を含む）と一致しない
  - `v-f1f19602d7` 生成値が URI 形式で、対象が管理する名前空間に属していることを申告で確認する
- **対照（negative control）**:
  - ★ 原文は大文字の MUST に小文字の not が続く（IIP-G03.a と同じ書式）。MUST NOT として扱う
  - ★ 『他の SAML provider すべて』との非衝突は原理的に確認できない。明白な違反（既知の entityID との一致）のみ自動検出し、残りは申告
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cc</code> の詳細</summary>

- **必要な variant**:
  - `v-439acc598f` 1 つの XML 文書内で、同じ xs:ID 値を宣言する要素が 2 つ以上存在しない（xs:ID の一意性制約）
  - `v-49c4a04e2d` 1 つの要素が同じ識別子を 2 つの属性で宣言していない（整形式・スキーマ制約）
  - `v-1053755a81` 文書が XML Schema 検証を通る（xs:ID の重複はスキーマ違反として現れる）
- **対照（negative control）**:
  - ★ 訂正: 前版は『<Response> と <Assertion> が同じ @ID を共有していない』『複数 <Assertion> が同じ @ID を持たない』を variant にしていたが、それは**異なるオブジェクトへ同じ ID を割り当てない**という別の規則で、IIP-SSO01.af / .ao の側に属する。本義務は『1 つのデータオブジェクトについて、ある識別子の宣言はちょうど 1 つ』であり、同一文書内の重複宣言・整形式・スキーマ制約として検査する
  - ★ 受動的な常時チェック。XML パーサが DTD / スキーマ検証を行わない実装でも成立させる必要がある
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cd</code> の詳細</summary>

- **必要な variant**:
  - `v-fbc12a9c02` 識別子の生成方式（乱数ビット長・エンコード）を申告で確認し、128 ビット以上のランダム値であることを確かめる
  - `v-c95ef0eae8` 観測した識別子群のエントロピー推定が 128 ビットを下回らない（明白な違反の自動検出）
  - `v-c9bbe44798` 対照: 明らかに短い識別子（16 進 8 桁など）を返す実装を検出できる
- **対照（negative control）**:
  - ★ 確率そのものは有限回の観測では証明できない。**BROWSER 観測では判定できない**ので ATTESTED とし、明白な違反（値が短すぎる・連番）だけを自動検出する
  - ★ 原文は『MAY be met by encoding a randomly chosen value between 128 and 160 bits』と実現手段を例示するが、これは MAY なので他の手段を FAIL にしない
  - ★ 非乱数方式（連番・ハッシュ由来）なら条件が偽で NOT_APPLICABLE。その場合も IIP-SSO01.af / .ao / .cc は無条件に適用される
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ce</code> の詳細</summary>

- **必要な variant**:
  - `v-ee5355ab6b` 識別子の生成方式が 160 ビット以上のランダム値であることを申告で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。128〜160 ビットの実装は MUST（.cd）を満たしつつ本 SHOULD を満たさない → WARNING
  - ★ .cd と本義務を 1 つにまとめると、128 ビット実装を FAIL にするか 160 ビット未達を見逃すかのどちらかになる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cf</code> の詳細</summary>

- **必要な variant**:
  - `v-1104edabf9` PRNG の seed 源（OS の CSPRNG 等）を申告で確認する
  - `v-84559e2e9c` 同一イメージから複製した 2 インスタンスが同じ識別子列を出さないことを申告で確認する
- **対照（negative control）**:
  - ★ seed の一意性は外部から観測できない。ATTESTED
  - ★ コンテナイメージの複製で seed が固定される事故は実在する。複製インスタンスでの重複を申告項目に入れる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cg</code> の詳細</summary>

- **必要な variant**:
  - `v-8a91460cff` 対象が送る <samlp:AuthnRequest> に @ID がある
  - `v-f6384d937c` @Version="2.0" である
  - `v-3015b6f281` @IssueInstant があり、SAML2Core 1.3.3 の UTC 表現である
  - `v-2249158315` 全 AuthnRequest が protocol schema 検証を通る
- **対照（negative control）**:
  - ★ 受動的な常時チェック。Transcript の全 AuthnRequest にスキーマ検証をかける
  - ★ 規範の出所は RFC2119 句ではなく**スキーマ文書**である（SAML2Core 1.1）
  - ★ 訂正: 前版は 1 義務に role [idp, sp] を持たせ、SP の AuthnRequest・IdP の Response・IdP の Assertion を variant として混在させていた。variant に role フィールドはないので、G2 で SP のケースが IdP 向け variant まで覆う必要があるように見えてしまう。role ごとに義務を分けた（.cg / .dv / .dw / .dx）
  - ★ プロキシ IdP が上流へ生成する AuthnRequest は .dx（条件付き）
  - ★ スキーマ検証は DTD を有効にせずに行う（IIP-G03 と整合させる）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dv</code> の詳細</summary>

- **必要な variant**:
  - `v-2481d14a7a` 対象が送る <samlp:Response> に @ID / @Version="2.0" / @IssueInstant がすべてある
  - `v-239d9ed5dc` <samlp:Status> がある（成功・失敗のいずれでも）
  - `v-096ce8d57a` @IssueInstant が SAML2Core 1.3.3 の UTC 表現である
  - `v-b0cee1b41e` 全 Response が protocol schema 検証を通る
- **対照（negative control）**:
  - ★ 受動的な常時チェック
  - ★ <Status> の中身（最上位 <StatusCode>/@Value）は IIP-SSO01.ch
  - ★ 受信側で不正な構文の要求に応答する場合の規則は IIP-SSO01.an
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dw</code> の詳細</summary>

- **必要な variant**:
  - `v-7aca1951cc` 対象が送る <saml:Assertion> に @Version="2.0" / @ID / @IssueInstant と <saml:Issuer> がすべてある
  - `v-6b41660dc3` <saml:AuthnStatement> に @AuthnInstant と <saml:AuthnContext> がある
  - `v-d929b808aa` @IssueInstant / @AuthnInstant が SAML2Core 1.3.3 の UTC 表現である
  - `v-cfac9ffeff` 全 assertion が assertion schema 検証を通る
- **対照（negative control）**:
  - ★ 受動的な常時チェック
  - ★ 受信側でスキーマ上不正な assertion を拒否する義務は IIP-SSO01.cx
  - ★ 暗号化された assertion は復号後に検証する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dx</code> の詳細</summary>

- **必要な variant**:
  - `v-d29cc8e957` 対象が上流 Samlier-IdP へ送る AuthnRequest に @ID / @Version="2.0" / @IssueInstant がすべてある
  - `v-7bfa203161` その AuthnRequest が protocol schema 検証を通る
- **対照（negative control）**:
  - ★ IIP-SSO01.cg は role が sp。プロキシ IdP が生成する要求はここで見る
  - ★ @ID の一意性は IIP-SSO01.dr
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ch</code> の詳細</summary>

- **必要な variant**:
  - `v-72267b6f05` 成功時の最上位 @Value が urn:oasis:names:tc:SAML:2.0:status:Success
  - `v-79f10410bd` エラー時の最上位 @Value が Requester / Responder / VersionMismatch のいずれか
  - `v-829adf3a27` 対照: 二次コード（AuthnFailed 等）を最上位に置いていない
- **対照（negative control）**:
  - ★ 二次 <StatusCode> の値は自由（原文は『responders MAY omit subordinate status codes』）。最上位だけを判定する
  - ★ 『二次コードを最上位に置く』は実装でよくある誤り。検出できるようにする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ci</code> の詳細</summary>

- **必要な variant**:
  - `v-c6c230fbbd` 対象が <saml:Statement> を出す場合、その要素に xsi:type がある
  - `v-c3783c1356` <saml:Statement> を出さない対象では空虚に真
- **対照（negative control）**:
  - ★ 受動的な常時チェック。<AuthnStatement> / <AttributeStatement> は具体型なので対象外
  - ★ 観測機会がない場合は satisfied_with_note とし『検証した』とは書かない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cj</code> の詳細</summary>

- **必要な variant**:
  - `v-b7e33728ee` statement のない assertion を返す構成で、その assertion に <saml:Subject> がある
  - `v-a07fa9b5a5` statement を含む assertion では <Subject> の有無を問わない（IIP-IDP11.a は NameID なしの Subject を扱う）
- **対照（negative control）**:
  - ★ Web Browser SSO では IIP-SSO01.l により <AuthnStatement> が必ず 1 つ以上あるので、本義務が独立した意味を持つのは追加の assertion を同梱する構成のみ
  - ★ 観測機会がない場合は satisfied_with_note
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ck</code> の詳細</summary>

- **必要な variant**:
  - `v-74188a7f1a` 対象が <saml:Condition> を出す場合、その要素に xsi:type がある
  - `v-2a2a609eb6` <saml:Condition> を出さない対象では空虚に真
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-SP07（AuthnContext による受理判断）とは無関係
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cl</code> の詳細</summary>

- **必要な variant**:
  - `v-7071e09eb6` 対象が <OneTimeUse> を出す構成で、1 つの <Conditions> に 1 つしかない
  - `v-7caa2520b2` <OneTimeUse> を出さない対象では空虚に真
- **対照（negative control）**:
  - ★ スキーマは複数を許すので、スキーマ検証では検出できない。要素数を数える
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cm</code> の詳細</summary>

- **必要な variant**:
  - `v-1bd24a4938` 対象が <ProxyRestriction> を出す構成で、1 つの <Conditions> に 1 つしかない
  - `v-1529b25479` <ProxyRestriction> を出さない対象では空虚に真
- **対照（negative control）**:
  - ★ スキーマは複数を許すので、スキーマ検証では検出できない。要素数を数える
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cn</code> の詳細</summary>

- **必要な variant**:
  - `v-469b18ff4a` 返る assertion の <Conditions>/@NotBefore < @NotOnOrAfter
  - `v-5f6e2fa0b4` 片方だけの構成、両方ない構成でも空虚に真
- **対照（negative control）**:
  - ★ 受動的な常時チェック。時刻の比較は UTC に正規化してから行う
  - ★ 妥当期間の長さは原文に規定がないので判定に使わない（IIP-G01 と同じ理由）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.co</code> の詳細</summary>

- **必要な variant**:
  - `v-73063d791a` Invalid: <Conditions>/@NotOnOrAfter を過ぎた assertion → 拒否される
  - `v-47e4b549b2` Invalid: <AudienceRestriction> が対象の entityID を含まない assertion → 拒否される
  - `v-fee96c9672` Indeterminate: 未知の <saml:Condition>（xsi:type が理解できない）を含む assertion → 拒否される
  - `v-dd6852cf2b` 対照: すべて妥当な assertion → 受理される
- **対照（negative control）**:
  - ★ 検出力の要は **Indeterminate**。未知の条件を『理解できないので無視する』実装は本 MUST に違反する。IIP-EXT01.b1（<Extensions> / <Advice> の内容は無視してよい）とは扱いが違うので混同しない
  - ★ IIP-SSO01.r（その他の点でも妥当であることを検証する）が Invalid 側を扱う。本義務は『判定の結果として拒否する』という帰結側で、特に Indeterminate を明示する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cp</code> の詳細</summary>

- **必要な variant**:
  - `v-b419b7cdcf` 対象の entityID を含む <AudienceRestriction> と、含まない別の <AudienceRestriction> を同梱 → 拒否される
  - `v-a2c1f32fce` 対照: すべての <AudienceRestriction> が対象の entityID を含む → 受理される
  - `v-100375ecaa` 1 つの <AudienceRestriction> 内に非該当 <Audience> と対象の entityID を併記 → 受理される（同一条件内は OR）
- **対照（negative control）**:
  - ★ 各 <AudienceRestriction> は独立に評価される（論理積）。『どれか 1 つに自分が入っていれば受理する』実装が典型的な違反
  - ★ 1 つの <AudienceRestriction> 内の複数 <Audience> は論理和。混同すると逆の誤判定をする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cq</code> の詳細</summary>

- **必要な variant**:
  - `v-b5c37e4580` <OneTimeUse> 付き assertion の処理が遅延なく行われることを申告で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。『直ちに』の閾値は原文にないので、時間を判定条件にしない
  - ★ 判定できる観測がないため申告のみ。satisfied は申告があるときだけ
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cr</code> の詳細</summary>

- **必要な variant**:
  - `v-89e1777da3` <OneTimeUse> 付き assertion をキャッシュ・永続化していないことを申告で確認する
  - `v-eb0e941099` 同じ assertion を再送 → 受理されない（IIP-SSO01.w の replay 検出と観測が重なる）
- **対照（negative control）**:
  - ★ 『保持していない』ことは外部から観測できない。ATTESTED
  - ★ 再送が拒否されても、それは replay 検出（IIP-SSO01.w）による可能性がある。本義務の証拠としては弱いので、申告と併せて記録する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cs</code> の詳細</summary>

- **必要な variant**:
  - `v-3d471daca7` assertion をキャッシュする実装が、<OneTimeUse> 付きのものは除外していることを申告で確認する
  - `v-2b9717f402` assertion を一切保持しない実装では空虚に真
- **対照（negative control）**:
  - ★ IIP-SSO01.cr（保持しない）と本義務（保持するなら遵守する）は同じ結論に至るが名宛人が違う。原文が両方を置いているので両方を持つ
  - ★ IIP-SSO01.w（replay 防止のための ID 保持）は <OneTimeUse> の『保持』とは別物。ID の記録は assertion の保持ではない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ct</code> の詳細</summary>

- **必要な variant**:
  - `v-25117bbf31` 上流 Samlier-IdP が <ProxyRestriction> 付き assertion を返す → 対象が発行する assertion がその制限に違反しない
  - `v-e2fed14b31` 対照: <ProxyRestriction> のない上流 assertion → 制限なしでよい
- **対照（negative control）**:
  - ★ 上流を Samlier が演じられるので自動観測できる。プロキシ構成が前提
  - ★ 具体的な違反は IIP-SSO01.cu（Count=0）と .cv（Count 減算）、.cw（Audience）で細分する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cu</code> の詳細</summary>

- **必要な variant**:
  - `v-ced0b9c695` 上流が <ProxyRestriction Count="0"> 付き assertion を返す → 対象は下流 Samlier-SP へ assertion を発行しない
  - `v-cc5f6bf29e` 対照: <ProxyRestriction Count="1"> → 発行してよい
- **対照（negative control）**:
  - ★ 対照がないと、そもそもプロキシしない実装と区別できない
  - ★ 発行しない場合の代替挙動（エラー応答）は原文が定めていないので判定に使わない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cv</code> の詳細</summary>

- **必要な variant**:
  - `v-5c91e3882a` 上流が Count="3" → 対象が発行する assertion の <ProxyRestriction>/@Count が 2 以下
  - `v-00edf71b63` 上流が Count="1" → 対象が発行する assertion の @Count が 0
  - `v-bad04ca342` 対照: <ProxyRestriction> を落として発行する実装を検出できる
- **対照（negative control）**:
  - ★ 『at most one less』なので 2 以上減らすのも適合。『ちょうど 1 減』を要求しない
  - ★ IIP-SSO01.ba（AuthnRequest の ProxyCount 減算）とは別物。あちらは要求、こちらは assertion
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cw</code> の詳細</summary>

- **必要な variant**:
  - `v-1a12006138` 【要件 1】上流が <ProxyRestriction> に <Audience>（下流 Samlier-SP の entityID）を含める → 対象が発行する assertion の <AudienceRestriction> にその値が 1 つ以上含まれる
  - `v-9f4aa358cb` 【要件 2】対象が発行する assertion の <AudienceRestriction> に、元の <ProxyRestriction> になかった <Audience> が含まれていない
  - `v-2aa42f7ace` 対照: 上流の <ProxyRestriction> に <Audience> が 1 つもない → audience 制限は課されない（本義務の対象外）
- **対照（negative control）**:
  - ★ 訂正 1: 前版は『元の <Audience> に含まれない相手には assertion を発行しない』としていたが、原文が直接要求するのは**発行する assertion の <AudienceRestriction> の中身**であって、発行そのものの禁止ではない。より強い義務にしていた
  - ★ 訂正 2: 原文後半の『and no <Audience> elements present that were not in the previous <ProxyRestriction> element』が variant になっていなかった。要件 2 として明示した
  - ★ 原文『If no <Audience> elements are specified, then no audience restrictions are imposed』により、<Audience> 不在の構成は対象外。両方の構成を試して分岐を確認する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cx</code> の詳細</summary>

- **必要な variant**:
  - `v-5d274deba0` @Version を欠いた assertion → 拒否される
  - `v-1eb0e2ddde` @IssueInstant を欠いた assertion → 拒否される
  - `v-276a3dd7ec` <saml:Issuer> を欠いた assertion → 拒否される
  - `v-70af1c7e5c` <saml:AuthnStatement>/@AuthnInstant を欠いた assertion → 拒否される
  - `v-8f9ba8d085` <saml:AuthnContext> を欠いた <AuthnStatement> → 拒否される
  - `v-ae02287dc7` @Version="1.1" の assertion → 拒否される
  - `v-5084718ecd` 対照: すべて揃った assertion → 受理される
- **対照（negative control）**:
  - ★ IIP-SSO01.dw（IdP 側の assertion スキーマ適合）と対になる受信側の義務
  - ★ 署名済み assertion から属性を削ると署名も壊れる。未署名構成でも試して『スキーマ違反で拒否した』のか『署名不正で拒否した』のかを切り分ける
  - ★ IIP-SSO01.co（Invalid / Indeterminate の拒否）は条件評価の結果、本義務は構文。原文の『just as if the assertion were malformed』が両者を結びつけている
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cy</code> の詳細</summary>

- **必要な variant**:
  - `v-e6f9a156ae` <saml:Issuer>（entity Format）に @NameQualifier / @SPNameQualifier が付いていない
  - `v-f715d6a6e8` Format=unspecified の <saml:NameID> に @NameQualifier / @SPNameQualifier が付いていない
  - `v-dadb3cd000` 対照: persistent Format では §8.3.7 が用法を定義しているので付いてよい（IIP-SSO05.a3）
  - `v-02e9b1a3f3` 対照: transient Format では §8.3.8 が MAY として認めているので付いてよい
- **対照（negative control）**:
  - ★ SHOULD_CLASS。付いていても WARNING であって FAIL ではない
  - ★ 対照が必須。persistent / transient で付いていることを違反にしてはならない
  - ★ 受動的な常時チェック
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.cz</code> の詳細</summary>

- **必要な variant**:
  - `v-0315eb94ab` <Subject> 直下の識別子が 1 つだけである（スキーマの choice 制約と一致）
  - `v-42c3515e03` 意味上の検査: <Subject>/<SubjectConfirmation> の中に置かれた識別子（<NameID> / <BaseID> / <EncryptedID>）が、<Subject> 直下の識別子と同じ主体を指している
  - `v-bc0be808a6` 複数の <SubjectConfirmation> があるとき、それぞれの識別子が同じ主体を指している
  - `v-4874b09546` <Subject> 直下の識別子と、<AttributeStatement> が返す主体識別属性が食い違っていない
  - `v-5f030059cc` 対照: 同じ主体を別 Format で表した識別子が併記されている構成は違反ではない（IIP-SSO01.i2 と同じ扱い）
- **対照（negative control）**:
  - ★ SHOULD_NOT
  - ★ 訂正: 前版は『識別子要素が 1 個であること』しか variant にしていなかったが、それは主にスキーマの choice 制約であって、原文が問題にする『2 人以上の主体』を検出しない。<SubjectConfirmation> 内の識別子など**意味上の複数主体**を見る variant を追加した
  - ★ 『同じ主体か』は値の一致ではない。Suite がログインさせた 1 人の主体に対応しているかで判定する
  - ★ 判定できない場合は not_verified。文字列比較だけで violated にしない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.da</code> の詳細</summary>

- **必要な variant**:
  - `v-6b79cdd25e` <SubjectConfirmationData> の未定義属性がすべて非 SAML 名前空間で修飾されている
  - `v-e87102ca51` 名前空間なしの独自属性が付いていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-EXT01.c（xsd:anyAttribute への未定義属性を無視してよい）と対。あちらは受信側の許容、こちらは生成側の禁止
  - ★ 拡張属性を出さない対象では空虚に真。satisfied_with_note とし『検証した』とは書かない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.db</code> の詳細</summary>

- **必要な variant**:
  - `v-ef4c390133` 【上限】<SubjectConfirmationData>/@NotOnOrAfter ≤ <Conditions>/@NotOnOrAfter
  - `v-f63b4b0bc7` 【下限】<SubjectConfirmationData>/@NotBefore ≥ <Conditions>/@NotBefore（非 bearer の確認方式で）
  - `v-16b70094d3` 両端がそろって <Conditions> の期間に含まれている
  - `v-b5deaf0f22` <Conditions> を持たない assertion、または該当属性がない場合は空虚に真
- **対照（negative control）**:
  - ★ SHOULD_CLASS。受動的な常時チェック
  - ★ 訂正: 前版は上限（@NotOnOrAfter）しか見ていなかった。本義務は一般の <SubjectConfirmationData> を対象にしており、bearer 以外の確認方式では @NotBefore を持てるので**下限も検査する**
  - ★ bearer では @NotBefore が禁止（IIP-SSO01.k1）なので、bearer だけの構成では上限のみが効く
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dc</code> の詳細</summary>

- **必要な variant**:
  - `v-864e6c9b54` bearer 以外の <SubjectConfirmation> を含む構成で、@NotBefore < @NotOnOrAfter
  - `v-e5fd9847f9` bearer の <SubjectConfirmationData> では @NotBefore が禁止（IIP-SSO01.k1）なので空虚に真
- **対照（negative control）**:
  - ★ Web Browser SSO の bearer では @NotBefore を置けないため、本義務が意味を持つのは追加の <SubjectConfirmation> を同梱する構成のみ
  - ★ IIP-SSO01.cn（<Conditions> の NotBefore < NotOnOrAfter）とは別要素。混同しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dd</code> の詳細</summary>

- **必要な variant**:
  - `v-00707d602b` 返る assertion に <AuthnStatement> があるとき、<Subject> もある
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-IDP11.a（Subject に NameID を含まない assertion を生成できる）とは別。あちらは <Subject> 内の <NameID> の有無、こちらは <Subject> 自体の有無
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.de</code> の詳細</summary>

- **必要な variant**:
  - `v-2dfa93df94` SessionIndex に主体を特定できる値（ユーザー名・メールアドレス・NameID）が含まれていない
  - `v-7956d587ff` 同一主体・別セッションの SessionIndex が同じ定数になっていない（主体固有の定数ではない）
  - `v-c3e682cd81` 相関可能性の判定: secondary_peer（別 SP）に同じ値が出た場合でも、その値が多数の主体で共有されるなら相関できない。値域と共有度を申告で確認する
  - `v-92d62e29d4` 対照: 方式 (b)（囲む assertion の @ID を使う）では SP 間で値が異なる。これも適合
- **対照（negative control）**:
  - ★ SHOULD_NOT。判定は『**主体を相関できるか**』であって『別 SP で値が異なるか』ではない
  - ★ 訂正: 前版は『secondary_peer に対して異なる SessionIndex が発行される』を必須 variant にしていたが、原文が推奨する方式 (a)（小さい正整数・繰り返し定数）は**多数の主体で同じ値を共有させて相関を防ぐ**方式で、SP 間で同値になりうる。同値であることを違反にすると適合実装を落とす
  - ★ 自動検出できるのは明白な違反（主体識別子そのものを入れている・主体固有の定数）だけ。値域の共有度は観測できないので申告に落とす
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.df</code> の詳細</summary>

- **必要な variant**:
  - `v-169279cef8` 方式 (a) を採る構成で、SessionIndex の値域の設計（範囲と 1 値あたりの主体数）を申告で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。値域の濃度は観測では確かめられないため申告のみ
  - ★ 『十分高い』の閾値は原文にないので数値条件にしない
  - ★ 訂正: 本 SHOULD は原文の**方式 (a)（小さい正整数・繰り返し定数）の内部規則**である。方式 (b)（囲む assertion の @ID を使う）を採る実装には適用されないので、述語 uses_small_integer_sessionindex を条件にした
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dg</code> の詳細</summary>

- **必要な variant**:
  - `v-61ae6739c8` 方式 (a) を採る構成で、連続する SSO の SessionIndex が連番になっていない
  - `v-3a828547a4` 同一セッション参加者への後続 statement で一意性を保つための例外を申告で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。連番の自動検出はできるが、原文の例外（同一参加者・別セッションでの一意性確保）があるので自動検出だけで violated にせず申告と併せる
  - ★ 訂正: 本 SHOULD も方式 (a) の内部規則。方式 (b) には適用されない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dh</code> の詳細</summary>

- **必要な variant**:
  - `v-5248a8c346` 属性を返す構成で、<AttributeStatement> を含む assertion に <Subject> がある
- **対照（negative control）**:
  - ★ 受動的な常時チェック
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.di</code> の詳細</summary>

- **必要な variant**:
  - `v-2948f8079f` <saml:Attribute> の未定義属性がすべて非 SAML 名前空間で修飾されている
  - `v-f9b1abb961` 名前空間なしの独自属性が付いていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-SP01（任意の Name / NameFormat の消費）とは別の、生成側の禁止
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dj</code> の詳細</summary>

- **必要な variant**:
  - `v-6a68e34b31` 値のない属性をリリースする構成で、<saml:Attribute> に <AttributeValue> が 0 個
  - `v-4e965c4b63` 対照: 値のある属性では <AttributeValue> がある
- **対照（negative control）**:
  - ★ 受動的な常時チェック。空の <AttributeValue/> を出す実装が典型的な違反
  - ★ 『値が空文字列』の場合は IIP-SSO01.dk（空要素を出す）であって省略ではない。両者を混同しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dk</code> の詳細</summary>

- **必要な variant**:
  - `v-35b7d51a2d` 空文字列の属性値をリリースする構成で、<AttributeValue/> が空要素として出る
- **対照（negative control）**:
  - ★ IIP-SSO01.dj（値がない → 要素を省略）と対。空値と無値を区別する
  - ★ 受動的な常時チェック
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dl</code> の詳細</summary>

- **必要な variant**:
  - `v-d1a7c6d493` null 値の属性をリリースする構成で、<AttributeValue xsi:nil="true"/> が出る
  - `v-6bacc25c24` その要素が空である（子ノードもテキストもない）
- **対照（negative control）**:
  - ★ 『空要素であること』と『xsi:nil を持つこと』の両方を見る
  - ★ null を扱わない対象では空虚に真。satisfied_with_note
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dm</code> の詳細</summary>

- **必要な variant**:
  - `v-a3612aeb7a` 対象が返す <saml:EncryptedAssertion> の <xenc:EncryptedData> に @Type がある
  - `v-fafb7e335b` <saml:EncryptedID> / <saml:EncryptedAttribute> を出す構成でも同様
- **対照（negative control）**:
  - ★ SHOULD_CLASS。IIP-IDP09.a により assertion の暗号化は対応必須なので観測機会がある
  - ★ 値の正しさは IIP-SSO01.dn
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dn</code> の詳細</summary>

- **必要な variant**:
  - `v-8cd0a3d779` @Type がある場合、その値が http://www.w3.org/2001/04/xmlenc#Element である
  - `v-dae889ba94` 対照: #Content を使っている実装を検出できる
- **対照（negative control）**:
  - ★ @Type がない場合は本義務の対象外（存在の SHOULD は IIP-SSO01.dm）
  - ★ #Content は EncryptedData の内容だけを置き換える形式で、SAML の暗号化要素では誤り
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.do</code> の詳細</summary>

- **必要な variant**:
  - `v-e7dffcd0f9` <saml:EncryptedAssertion> を復号すると AssertionType（またはその派生型）の要素が現れる
  - `v-524c5ff844` <saml:EncryptedID> を復号すると NameIDType **または AssertionType**、あるいは BaseIDAbstractType / NameIDType / AssertionType から派生した型の要素が現れる
  - `v-ac530e0e8a` <saml:EncryptedAttribute> を復号すると AttributeType（またはその派生型）の要素が現れる
  - `v-46b0d6076a` 対照: <EncryptedID> に assertion を入れる構成（原文が明示的に認めている）を違反にしない
- **対照（negative control）**:
  - ★ 訂正: 前版は <EncryptedID> の許容型から **AssertionType** を落としていた。原文は『an element that has a type of NameIDType or AssertionType, or a type that is derived from BaseIDAbstractType, NameIDType, or AssertionType』であり、『an entire assertion can be encrypted into this element and used as an identifier』と補足している
  - ★ 復号鍵を Suite が持つ構成でのみ観測できる。Test Peer の暗号鍵で暗号化させる
  - ★ 復号できない場合は not_verified。対象の不適合ではない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dp</code> の詳細</summary>

- **必要な variant**:
  - `v-2bae957fc4` 同じ persistent NameID を 2 回暗号化させる → <saml:EncryptedID> の <xenc:CipherValue> が毎回異なる
  - `v-46c83eb407` 同一主体・同一 SP で連続して SSO → 毎回異なる暗号文になる
  - `v-aaa8cae195` 対照: 平文が異なれば当然異なる（この対照だけでは決定的暗号を検出できない）
- **対照（negative control）**:
  - ★ 検出力の要: **同一の平文**を 2 回暗号化させないと、決定的な暗号化（同じ IV / ECB）を検出できない。transient NameID は毎回変わるので平文が変わってしまう。persistent NameID を使う
  - ★ 訂正: 前版は『暗号文一般』の義務にしていたが、この MUST は **<EncryptedID>（§2.2.4）にだけ置かれた規則**である。§2.3.4 <EncryptedAssertion> と §2.7.3.2 <EncryptedAttribute> に同じ文はない。対象を <EncryptedID> に限定した
  - ★ <EncryptedAssertion> / <EncryptedAttribute> の暗号文の一意性は [XMLEnc] 側の問題であり、IIP-SSO01 が取り込む範囲ではないので advisory として記録する
  - ★ 受動規則として扱う。対象が実際に送出した該当要素ごとに検査し、当該 Run で 1 件も観測されなければ satisfied_with_note（観測機会なし）とする。**条件述語を置かない**: 肯定的な観測材料しか作れない CAPABILITY_BASED 述語では『機能を持たない』ことを FALSE にできず（申告のみの false は UNKNOWN）、非対応の対象が永久に not_verified になる。IIP-SSO01.er 等と同じ扱いに揃えた
  - ★ この規則は IV の再利用という実際の脆弱性に対応する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dq</code> の詳細</summary>

- **必要な variant**:
  - `v-bf46973f80` <xenc:EncryptedKey>/@Recipient がある
  - `v-b831bbe8f9` その値が Test Peer の entityID と一致する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。2 つの SHOULD（存在・値）を 1 義務にまとめている。存在しなければ値も評価できないため分岐がなく、判定は同じ
  - ★ 復号鍵が複数ある構成（IIP-SP08.b / IIP-IDP19.b）では、Recipient が鍵選択の手掛かりになる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ds</code> の詳細</summary>

- **必要な variant**:
  - `v-b9b7756dd8` 対象が @Address を出す構成で、IPv4 がドット 10 進表記である
  - `v-af08bb9bec` IPv6 が RFC 3513 の表記である（短縮形を含む）
  - `v-617ae11670` @Address を出さない対象では空虚に真
- **対照（negative control）**:
  - ★ SHOULD_CLASS。@Address 自体は MAY なので、出さないことは違反ではない
  - ★ <SubjectConfirmationData>/@Address と <SubjectLocality>/@Address の両方が対象。同じ規則なので 1 義務にまとめ、根拠を 2 か所から引いている
  - ★ 受動的な常時チェック
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.du</code> の詳細</summary>

- **必要な variant**:
  - `v-afea42ac81` 多値属性（例: eduPersonAffiliation に 2 値）をリリースする構成で、<AttributeValue> が値の数だけある
  - `v-017b66d353` 1 つの <AttributeValue> に区切り文字（; や ,）で複数値を詰めていない
  - `v-f5a23e7143` 単一値の属性では <AttributeValue> が 1 つ（空虚に真）
- **対照（negative control）**:
  - ★ SHOULD_CLASS（RECOMMENDED）。区切り文字で詰めても WARNING であって FAIL ではない
  - ★ 受動的な常時チェック。多値属性をリリースできない対象では観測機会がないので satisfied_with_note とし『検証した』とは書かない
  - ★ 同節の『複数の <AttributeValue> に xsi:type があるなら全て同一型でなければならない』は小文字の must で RFC2119 キーワードではないため、advisory として記録する
  - ★ IIP-SSO01.dj（値がない → 要素を省略）/ .dk（空値 → 空要素）と混同しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dy</code> の詳細</summary>

- **必要な variant**:
  - `v-0eeefee5af` 方式 (a): SessionIndex が小さい正整数・繰り返し定数の集合から選ばれている
  - `v-1ca2c597da` 方式 (b): SessionIndex が囲む <saml:Assertion>/@ID と一致している
  - `v-da2bb5aece` いずれかを採っていることを申告と観測で確認する
- **対照（negative control）**:
  - ★ SHOULD_CLASS（RECOMMENDED）。どちらの方式でも適合であり、**どちらも採っていない**場合に WARNING になる
  - ★ 方式 (b) は自動判定できる（SessionIndex == Assertion/@ID）。方式 (a) は申告が要る
  - ★ 目的は IIP-SSO01.de（相関できないこと）。本義務はその実現方式の推奨であって、第 3 の方式で相関を防いでいる実装を FAIL にしない。その場合は de を満たしていれば 本義務は satisfied_with_note とする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dz</code> の詳細</summary>

- **必要な variant**:
  - `v-322dc709d5` 対象が送る全メッセージ・assertion の xs:string 型の要素内容・属性値が、空でも空白のみでもない
  - `v-78f40ab103` <saml:AttributeValue> の空値は例外（IIP-SSO01.dk が明示的に空要素を求めている）
- **対照（negative control）**:
  - ★ 受動的な常時チェック
  - ★ 原文の『Unless otherwise noted』が効く箇所を除外リストにする。<AttributeValue> の空値・null 値（IIP-SSO01.dk / .dl）が該当する。除外を明示しないと、原文が求める空要素を違反にしてしまう
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ea</code> の詳細</summary>

- **必要な variant**:
  - `v-54da00f2a2` entityID・NameID・属性値の比較がバイト単位で行われることを申告で確認する
  - `v-5a9a5ac32b` 大文字小文字が異なるだけの entityID を持つメタデータを別実体として扱う（自動観測できる場合）
- **対照（negative control）**:
  - ★ 比較方式は内部処理なので原則 ATTESTED。観測できる帰結は IIP-SSO01.eb に分けた
  - ★ IIP-IDP21.a（大文字小文字だけが異なる識別子を別主体に割り当てない配備）とは方向が逆。あちらは配備の運用、こちらは実装の比較方式
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eb</code> の詳細</summary>

- **必要な variant**:
  - `v-33855481ad` persistent NameID の大文字小文字を変えた値 → 別の主体として扱われる（同一視されない）
  - `v-2a6c529d41` entityID の末尾に空白を足した値 → 一致しない（トリムされない）
  - `v-6c646e4699` 属性値の前後の空白が保持される
  - `v-9f6a001fea` 対照: 完全に一致する値 → 一致する
- **対照（negative control）**:
  - ★ IIP-SSO01.ea（比較方式そのもの）の観測可能な帰結。こちらは自動判定できる
  - ★ XML 属性値の正規化（[XML] 3.3.3）は XML パーサが行うもので、本義務の『トリム』とは別。比較は XML 解析後の値で行う（IIP-G02.a と同じ扱い）
  - ★ 大文字小文字を同一視する実装はアカウント乗っ取りに直結する。mutant SUT の候補にする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ec</code> の詳細</summary>

- **必要な variant**:
  - `v-18fdbaae87` 異なる符号化の入力（UTF-8 / UTF-16 等）を比較する経路の有無と、その比較方式を申告で確認する
  - `v-738904b6dd` 結合文字を含む識別子（NFC と NFD で異なる表現）を送ったとき、NFC 正規化＋バイナリ比較と**同じ結果**になる（一致すべきものが一致し、しないものが一致しない）
- **対照（negative control）**:
  - ★ 内部の比較方式なので ATTESTED。IIP-G02.a の『結合文字（正規化で長さが変わる）』variant と観測を共有できる
  - ★ 訂正: 前版は『NFD へ正規化する実装は違反』としていたが、原文が求めるのは**NFC + バイナリ比較と同じ結果を返す方法**であって、内部の正規化形式ではない。内部で NFD 正規化していても比較結果が同じなら違反ではない。判定は結果の同値性で行う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ed</code> の詳細</summary>

- **必要な variant**:
  - `v-92c4a166d9` 改行を含む要素内容を外部データ（LDAP 等）と突き合わせる経路で、XML の行末正規化（CRLF → LF、[XML] 2.11）が起きることを織り込んでいることを申告で確認する
  - `v-b8ed368f19` XML 属性値中の TAB / 改行が空白に置き換わること（[XML] 3.3.3）を織り込んでいることを申告で確認する
  - `v-c2cb44654c` その結果、SAML 側の値と外部データが正しく突き合わされる
- **対照（negative control）**:
  - ★ 外部データとの突き合わせは Suite から観測できない。ATTESTED
  - ★ 訂正: 前版は『空白に置き換わることを前提にしていないことを確認』と書いており方向が逆だった。義務は**正規化が起きることを考慮する**ことであって、起きないことを前提にしないことではない
  - ★ IIP-G02.a の『リテラル TAB/LF は XML 属性値正規化で空白になる』と同じ現象を扱う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ee</code> の詳細</summary>

- **必要な variant**:
  - `v-41095afdfb` 値の集合を扱う処理が、ロケール依存の照合順（大文字小文字順・アクセント順・言語別の並び）に依存していないことを申告で確認する
  - `v-1c0ba77354` ロケール設定を変えても同じ入力に対して同じ結果になることを申告で確認する
  - `v-275522ce6a` そもそも照合・ソートを行わない実装は本義務を満たす
- **対照（negative control）**:
  - ★ 訂正: 前版は『先頭の <AttributeValue> だけを使う』『文書順を入れ替える』を variant にしていたが、原文が禁じているのは**ロケール等で変わる照合・ソート順への依存**であって、XML 文書内の並び順の話ではない。ソート処理を一切しない実装まで違反扱いになりかねなかった
  - ★ 文書順への依存が問題になるかは原文が定めていない。判定に使わず advisory に記録する
  - ★ ロケール依存の照合順は Suite からは観測できないので ATTESTED
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ef</code> の詳細</summary>

- **必要な variant**:
  - `v-9c5ebee48b` 対象が送る entityID / Format / StatusCode/@Value / AuthnContextClassRef / Destination / AssertionConsumerServiceURL がすべて絶対 URI である
  - `v-acf4112fbd` 空・空白のみの URI 値がない
  - `v-a3dc6c71b4` 相対 URI（/acs のような値）を使っていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。スキーマの xs:anyURI は相対 URI も通すので、スキーマ検証では検出できない
  - ★ <ds:Reference>/@URI の same-document reference（#foo）は XML Signature 側の規定であり、SAML 定義の要素・属性ではないので対象外（IIP-SSO01.ev が別に扱う）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eg</code> の詳細</summary>

- **必要な variant**:
  - `v-dddca23090` IssueInstant / AuthnInstant / NotBefore / NotOnOrAfter / SessionNotOnOrAfter / validUntil が すべて末尾 Z の UTC 表記である
  - `v-651254739b` +09:00 のようなオフセット付き表記がない
  - `v-cd5b6c4901` タイムゾーン指定のない裸の表記がない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。スキーマの xs:dateTime はオフセット付きも通すので、スキーマ検証では検出できない
  - ★ IIP-G01（クロックスキュー）とは別。あちらは値の解釈、こちらは表記形式
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eh</code> の詳細</summary>

- **必要な variant**:
  - `v-c7ca1c01bc` 時刻比較でミリ秒より細かい桁を有意に扱っていないことを申告で確認する
  - `v-b2ed845786` マイクロ秒以下の桁を持つ時刻を送っても処理が変わらない（自動観測できる場合）
- **対照（negative control）**:
  - ★ SHOULD_NOT。『生成しない』ではなく『依拠しない』なので、細かい桁を出すこと自体は違反ではない
  - ★ マイクロ秒桁を付けた時刻を Suite から送って挙動が変わらないかを見れば自動観測に格上げできる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ei</code> の詳細</summary>

- **必要な variant**:
  - `v-be92b7e7e7` 対象が送る全時刻値の秒部分が 60 以上でない（:60 や :61 がない）
- **対照（negative control）**:
  - ★ 受動的な常時チェック。xs:dateTime のスキーマは秒 60 を許すので、スキーマ検証では検出できない
  - ★ うるう秒の発生日でないと自然には観測できないが、生成側の常時チェックとして全 Run で走らせる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ej</code> の詳細</summary>

- **必要な variant**:
  - `v-e8e260ec7f` 対象が送る全 assertion の @Version が、対象が申告した対応バージョンの集合に含まれる
  - `v-d60de0797a` IIP の対象範囲では 2.0 のみ
- **対照（negative control）**:
  - ★ 受動的な常時チェック。IIP-SSO01.dw（スキーマ適合）は @Version の存在を見るが、本義務は『対象が対応している値か』を見る
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ek</code> の詳細</summary>

- **必要な variant**:
  - `v-04670f5552` @Version="1.1" の assertion → 処理されない（セッションが確立しない）
  - `v-90c5beac19` @Version="3.0" の assertion → 処理されない
  - `v-dc178ae836` 対照: @Version="2.0" → 処理される
- **対照（negative control）**:
  - ★ 『処理しない』は『拒否する』より広い。属性を取り込まない・セッションを作らないことを見る
  - ★ IIP-SSO01.cx（スキーマ上不正な assertion の拒否）と重なるが、@Version="1.1" は 2.0 スキーマでは妥当でないため、実際には両方の理由が成立する。ケースは共有し、判定は義務ごとに行う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.el</code> の詳細</summary>

- **必要な variant**:
  - `v-b9f926ec6c` 対象が送る AuthnRequest の @Version が、対象が処理できる応答バージョンと整合している
  - `v-803599bac3` IIP の対象範囲では 2.0 のみなので、2.0 を出しつつ 2.0 応答を処理できることを確認する
- **対照（negative control）**:
  - ★ 受動的な常時チェック。単一バージョンしか扱わない実装では自明に満たされる
  - ★ 『2.0 の要求を出しながら 2.0 の応答を処理できない』構成が違反。IIP-SSO01.a の正常系が通れば満たされている
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.em</code> の詳細</summary>

- **必要な variant**:
  - `v-90256d17b5` @Version="1.1" の AuthnRequest → 拒否される
  - `v-eceb739eab` @Version="3.0" の AuthnRequest → 拒否される
  - `v-257ae49980` 対照: @Version="2.0" → 受理される
- **対照（negative control）**:
  - ★ 応答する場合の status code は IIP-SSO01.ep（VersionMismatch）
  - ★ IIP-SSO01.an（不正な要求への応答一般）と重なるが、こちらはバージョン固有
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.en</code> の詳細</summary>

- **必要な variant**:
  - `v-bff789a13d` @Version="2.0" の要求に対し、応答の @Version が 2.0 以下である
  - `v-d556517f92` unsolicited 応答は対応する要求がないので本義務の対象外
- **対照（negative control）**:
  - ★ 受動的な常時チェック。単一バージョン実装では自明に満たされる
  - ★ 将来 2.1 等が出た場合に意味を持つ義務。現時点では退行検出として置く
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eo</code> の詳細</summary>

- **必要な variant**:
  - `v-1d3b5a5655` @Version="2.0" の要求に対し、応答の major が 2 以上である
  - `v-7d7f7b3903` 対照: 二次コードが urn:oasis:names:tc:SAML:2.0:status:RequestVersionTooHigh の場合に限り、低い major の応答でよい（原文の except 節）
  - `v-0d94ddfe95` 対照: 二次コードが RequestVersionTooLow / RequestVersionDeprecated の場合は例外にならない
- **対照（negative control）**:
  - ★ 訂正: 前版は例外を『VersionMismatch の報告』と書いていたが、原文の except 節は **二次コード RequestVersionTooHigh** に限定されている。VersionMismatch は同節の別の箇条（最上位コードの規定。IIP-SSO01.ep）から取ってきた誤りで、RequestVersionTooLow でも低い major の応答を許してしまっていた
  - ★ except 節を対照に入れないと、正しくエラー報告している実装を FAIL にする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ep</code> の詳細</summary>

- **必要な variant**:
  - `v-1939f372ab` @Version="1.1" の AuthnRequest → 応答するなら最上位 @Value が urn:oasis:names:tc:SAML:2.0:status:VersionMismatch
  - `v-16e91ca42a` 対照: バージョン以外の理由のエラーでは VersionMismatch を使っていない
- **対照（negative control）**:
  - ★ 二次コードは MAY（原文は『MAY result in ...』と続く）。最上位だけを判定する
  - ★ IIP-SSO01.ch（最上位が top-level リストの値）と重なるが、こちらは値の特定まで求める
  - ★ 応答しない実装（HTTP エラーで打ち切る）は IIP-SSO01.an と同じ扱いで、本義務の違反にしない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eq</code> の詳細</summary>

- **必要な variant**:
  - `v-8202afbefe` 対象が送る <samlp:Response>（@Version="2.0"）内の全 assertion の @Version が 2.x である
  - `v-de89dbdbf7` V1.x 名前空間（urn:oasis:names:tc:SAML:1.0:assertion）の assertion が含まれていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。名前空間と @Version の両方を見る
  - ★ IIP-SSO01.i1（全 assertion が同一エンティティ発行）とは別の観点
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fg</code> の詳細</summary>

- **必要な variant**:
  - `v-2f6ec0a885` 対象が送る AuthnRequest の @Version が、双方が対応する最高版である
  - `v-dd3855b4b4` IIP の対象範囲は SAML 2.0 のみなので、実際には 2.0 であること
- **対照（negative control）**:
  - ★ SHOULD_CLASS。受動的な常時チェック
  - ★ IIP v1.1 が対象とするのは SAML 2.0 単一版なので、現時点では 2.0 を出していれば満たされる。**判定対象から外さずに残している**のは、将来 2.1 等が出たときに退行検出として効くため
  - ★ 相手の対応版を知る手段はメタデータにないので、実質的に『2.0 を出しているか』の確認になる
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fh</code> の詳細</summary>

- **必要な variant**:
  - `v-a55f89e5d6` 相手の対応版が不明なピアに対しても、自身の最高版（2.0）で要求を出す方針であることを申告で確認する
  - `v-d794a3ad93` 版を下げて要求を出す設定が既定になっていない
- **対照（negative control）**:
  - ★ SHOULD_CLASS。方針は内部の設定なので ATTESTED
  - ★ IIP の対象範囲は SAML 2.0 単一版なので、現時点では自明に満たされる。**判定対象から外さずに残している**のは、原文照合表から無言で落とさないため
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.er</code> の詳細</summary>

- **必要な variant**:
  - `v-6fb80f5707` 対象が付ける <ds:Signature> が署名対象要素の子であり、enveloped-signature transform を含む
  - `v-b5451cd21c` enveloping 署名（<ds:Object> に対象を入れる形式）を使っていない
  - `v-9e30363ca6` detached 署名（対象が署名の外にある形式）を使っていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック
  - ★ HTTP-Redirect バインディングのクエリ署名は XML Signature ではないので対象外
  - ★ 訂正: 前版は述語 target_signs_saml_messages を条件にしていたが、§5.4 の制約は『署名能力がある製品』ではなく**実際に生成された各 XML 署名**に適用される実行時条件である。能力はあるがこの要求では署名しない SP を declared=true / observed=false の矛盾として扱うのは誤りだった。条件を外し、**対象が送出した各署名を受動的に検査する**形にした
  - ★ 当該 Run で対象が XML 署名を 1 つも出していない場合は satisfied_with_note（観測機会なし）とし、『検証した』とは書かない。NOT_APPLICABLE にはしない（義務は適用されている）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.es</code> の詳細</summary>

- **必要な variant**:
  - `v-7857133d98` Web Browser SSO では assertion がブラウザ経由で届くので本 SHOULD が適用される → assertion が署名されている
  - `v-1b643be9b5` 対照: Response 署名のみでも『継承』として扱える（§5.3）
- **対照（negative control）**:
  - ★ SHOULD_CLASS。POST バインディングでは IIP-SSO01.v が MUST として同等の内容を要求するため、本義務が独立した意味を持つのは Artifact バインディングの場合
  - ★ §5.3 の署名継承（囲む要素の署名が assertion に及ぶ）は小文字 should の記述なので、『継承で足りるか』を判定に使わず、E26 が明示した『Response 署名でもよい』（IIP-SSO01.v）に従う
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.et</code> の詳細</summary>

- **必要な variant**:
  - `v-4f03aadc66` ブラウザ経由で届く <samlp:Response> に <ds:Signature> がある
  - `v-a49ee6550b` 対照: 各 assertion の署名だけでも POST バインディングでは IIP-SSO01.v を満たすが、本 SHOULD は <Response> 自体の署名を推奨している
- **対照（negative control）**:
  - ★ SHOULD_CLASS。Web Browser SSO は常にブラウザ（発信者以外）を経由するので適用される
  - ★ 訂正: 前版は role を [idp, sp] とし、SP が生成する AuthnRequest と IdP が生成する <Response> を同じ variant 集合に混ぜていた。variant に role フィールドはないので、IIP-SSO01.cg を分割したのと同じ理由で role 別に分けた（SP 側は IIP-SSO01.fj）
  - ★ IIP-SP13（SP が未署名 Response を拒否できる）とは別。あちらは受信側の能力
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fj</code> の詳細</summary>

- **必要な variant**:
  - `v-9957aefbae` HTTP-Redirect / HTTP-POST でブラウザ経由する AuthnRequest → XML 署名または binding 固有署名で保護される
  - `v-c2ec4670c4` HTTP-Redirect バインディングではクエリ署名でもよい（[SAML2Bind] の署名機構）
  - `v-1bda8953c7` HTTP-Artifact では artifact 解決の同期 binding が送信者認証と完全性保護を提供すれば、AuthnRequest の XML 署名は不要
- **対照（negative control）**:
  - ★ SHOULD_CLASS。AuthnRequest の署名は SAML2Prof 4.1.4.1 では MAY だが、Core §5 は SHOULD。強い方（SHOULD）を採る。未署名でも WARNING であって FAIL ではない
  - ★ Core 3.4.1 は『signed **or otherwise authenticated and integrity protected by the binding**』と選言である。署名経路だけを required にすると、相互認証された Artifact 解決を使う適合実装を WARNING にしてしまう
  - ★ Redirect のクエリ署名は XML 署名ではないので、IIP-SSO01.er / .eu / .ev / .ew / .ex の対象外。本義務は『署名されているか』だけを見るので、どちらの署名機構でも満たされる
  - ★ IdP のメタデータの WantAuthnRequestsSigned は MAY（SAML2Prof 4.1.6）。それを根拠に MUST へ引き上げない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.eu</code> の詳細</summary>

- **必要な variant**:
  - `v-51273c38e4` 対象が署名した要素のルートに @ID がある（何を署名するかは role により異なるが、判定は同じ）
  - `v-ac2e08fb50` その @ID が空でない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。**role 中立**の判定にしてある: 『対象が署名した要素』を見るので、IdP なら <Response> / <Assertion>、SP なら <AuthnRequest> が自動的に対象になる。role 固有の variant を混在させていないので G2 で片方の role が他方の variant を覆う必要は生じない
  - ★ IIP-SSO01.cg / .dv / .dw（スキーマ適合）と重なるが、こちらは署名の前提としての ID
  - ★ 訂正: 前版は述語 target_signs_saml_messages を条件にしていたが、§5.4 の制約は『署名能力がある製品』ではなく**実際に生成された各 XML 署名**に適用される実行時条件である。能力はあるがこの要求では署名しない SP を declared=true / observed=false の矛盾として扱うのは誤りだった。条件を外し、**対象が送出した各署名を受動的に検査する**形にした
  - ★ 当該 Run で対象が XML 署名を 1 つも出していない場合は satisfied_with_note（観測機会なし）とし、『検証した』とは書かない。NOT_APPLICABLE にはしない（義務は適用されている）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ev</code> の詳細</summary>

- **必要な variant**:
  - `v-30dbd8cac6` 対象が付ける署名の <ds:Reference> がちょうど 1 つである
  - `v-f48a398809` その @URI が "#" + 署名対象ルート要素の @ID と完全一致する
  - `v-76db3ed2fd` @URI が空（文書全体）や外部 URI になっていない
- **対照（negative control）**:
  - ★ 受動的な常時チェック。**XML Signature Wrapping への直接の防御**にあたる規則
  - ★ 判定は role 中立（対象が署名した要素を見る）
  - ★ 受信側では、署名が実際に処理対象の要素を覆っているかを確認する必要がある。その義務は IIP-SSO01.ey（許可外 transform 時の保証）と本義務の組で表す
  - ★ <ds:Reference> が複数ある署名を検出できること
  - ★ 訂正: 前版は述語 target_signs_saml_messages を条件にしていたが、§5.4 の制約は『署名能力がある製品』ではなく**実際に生成された各 XML 署名**に適用される実行時条件である。能力はあるがこの要求では署名しない SP を declared=true / observed=false の矛盾として扱うのは誤りだった。条件を外し、**対象が送出した各署名を受動的に検査する**形にした
  - ★ 当該 Run で対象が XML 署名を 1 つも出していない場合は satisfied_with_note（観測機会なし）とし、『検証した』とは書かない。NOT_APPLICABLE にはしない（義務は適用されている）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ew</code> の詳細</summary>

- **必要な variant**:
  - `v-dcedb1f007` <ds:CanonicalizationMethod>/@Algorithm が http://www.w3.org/2001/10/xml-exc-c14n#（または #WithComments）である
  - `v-270667842a` <ds:Transform> にも Exclusive Canonicalization が含まれる
- **対照（negative control）**:
  - ★ SHOULD_CLASS。Inclusive C14N でも WARNING であって FAIL ではない
  - ★ 受動的な常時チェック。判定は role 中立
  - ★ 訂正: 前版は述語 target_signs_saml_messages を条件にしていたが、§5.4 の制約は『署名能力がある製品』ではなく**実際に生成された各 XML 署名**に適用される実行時条件である。能力はあるがこの要求では署名しない SP を declared=true / observed=false の矛盾として扱うのは誤りだった。条件を外し、**対象が送出した各署名を受動的に検査する**形にした
  - ★ 当該 Run で対象が XML 署名を 1 つも出していない場合は satisfied_with_note（観測機会なし）とし、『検証した』とは書かない。NOT_APPLICABLE にはしない（義務は適用されている）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ex</code> の詳細</summary>

- **必要な variant**:
  - `v-9ac614df2f` 対象が付ける署名の <ds:Transforms> が enveloped-signature と exclusive c14n だけからなる
  - `v-800059e624` XPath / XSLT transform を含んでいない
- **対照（negative control）**:
  - ★ SHOULD_NOT。生成側の規則。判定は role 中立
  - ★ 受信側で許可外 transform をどう扱うかは IIP-SSO01.ey
  - ★ 訂正: 前版は述語 target_signs_saml_messages を条件にしていたが、§5.4 の制約は『署名能力がある製品』ではなく**実際に生成された各 XML 署名**に適用される実行時条件である。能力はあるがこの要求では署名しない SP を declared=true / observed=false の矛盾として扱うのは誤りだった。条件を外し、**対象が送出した各署名を受動的に検査する**形にした
  - ★ 当該 Run で対象が XML 署名を 1 つも出していない場合は satisfied_with_note（観測機会なし）とし、『検証した』とは書かない。NOT_APPLICABLE にはしない（義務は適用されている）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ey</code> の詳細</summary>

- **必要な variant**:
  - `v-60ca0314a1` XPath transform で <saml:AttributeStatement> を署名対象から**除外した** assertion → 拒否される
  - `v-68f9c00e5f` XPath transform で <saml:Conditions> を署名対象から除外した assertion → 拒否される
  - `v-346457c086` XSLT transform で <Response> の一部を除外した応答 → 拒否される
  - `v-a2d89cfe68` 署名対象を空にする transform を含む署名 → 拒否される
  - `v-4710f10be2` <ds:Reference>/@URI を空（文書全体）にしたうえで XPath で一部を除外した署名 → 拒否される
  - `v-a3ae670ae7` 対照: 許可された transform だけの正しい署名 → 受理される
- **対照（negative control）**:
  - ★ **XML Signature Wrapping の中核的な防御**。P0 相当の検出力を持たせる
  - ★ 訂正 1: 前版は role を [idp, sp] とし、AttributeStatement を除外する**応答**を主要 variant にしていた。それは SP の <Response> 検証しか試せず、IdP の AuthnRequest 検証を証明できない。role 別に分けた（IdP 側は IIP-SSO01.fk）
  - ★ 訂正 2: 前版は『許可外 transform を含むが内容を一切除外していない署名（恒等な XPath）→ 受理してよい』をrequired variant に置いていたが、原文は**内容を除外していなくても拒否してよい**としている（MAY）。受理・拒否の二択であり verdict を付けられない。Suite 側 fixture の自己検証に移した。自己検証で確かめるのは **(a) fixture の署名が暗号学的に正しいこと** と **(b) 恒等 transform が内容を除外していないこと** の 2 点だけで、対象の拒否理由や受理可否は自己検証の対象にしない
  - ★ 対象は**許可外 transform の存在だけを理由に拒否しても適合**なので、拒否理由を区別する必要はない
  - ★ 評価規則:
  -    拒否した → satisfied（原文の MAY 側）
  -    受理したが署名対象から内容を何も除外していない → satisfied
  -    **内容を除外した署名を受理した → violated**（除外内容の利用の有無に関係ない）
  -    除外の有無を確認できない → not_verified
  - ★ 原文が要求するのは『no content of the SAML message is excluded from the signature』であって、除外された内容を使わないことではない
  - ★ 『受理した』の観測は、対象が当該 assertion / メッセージを処理して先へ進んだこと（セッション確立・属性の反映）で判定する
  - ★ IIP-SSO01.ev（単一 <ds:Reference> が対象ルートを指す）と組で、署名が実際に処理対象を覆っているかを見る
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fk</code> の詳細</summary>

- **必要な variant**:
  - `v-bf7f033981` XPath transform で @AssertionConsumerServiceURL を署名対象から**除外した** AuthnRequest → 拒否される
  - `v-1d847b9f7f` XPath transform で <samlp:NameIDPolicy> を署名対象から除外した AuthnRequest → 拒否される
  - `v-c0b791effb` XPath transform で <samlp:Scoping> を署名対象から除外した AuthnRequest → 拒否される
  - `v-0ea8922de8` 署名対象を空にする transform を含む AuthnRequest の署名 → 拒否される
  - `v-00d2cc8f7a` 対照: 許可された transform だけの正しい署名の AuthnRequest → 受理される
- **対照（negative control）**:
  - ★ ACS URL を署名対象から外す攻撃は、署名済み要求を信頼して応答先を決める実装に直接効く（IIP-SSO01.aj / IIP-IDP12.b と連動する）
  - ★ 評価規則は IIP-SSO01.ey と同じ（拒否 → satisfied ／ 除外なしで受理 → satisfied ／ 除外ありで受理 → violated ／ 除外の有無を確認できない → not_verified）
  - ★ 恒等な transform のケースは required variant にしない（受理・拒否の二択で検出力がない）。Suite 側 fixture の自己検証に移した。自己検証で確かめるのは **(a) fixture の署名が暗号学的に正しいこと** と **(b) 恒等 transform が内容を除外していないこと** の 2 点だけで、対象の拒否理由や受理可否は自己検証の対象にしない
  - ★ 訂正: 前版は『未署名 AuthnRequest を受理する構成では観測機会がない』としていたが誤り。**対象が署名を必須にしているかどうかと、受信した署名を正しく検証する義務は別**であり、Suite は常に署名済み AuthnRequest を送れる。この記述を残すと**署名を一切検証せず常に受理する IdP を『観測機会なし』で逃がしてしまう**
  - ★ Suite は本義務のケースで**必ず署名済み AuthnRequest を送る**。Suite SP の鍵を対象に信頼させられない場合（メタデータを登録できない等）に限り not_verified(test_precondition_signing_key_not_trusted) とする
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ez</code> の詳細</summary>

- **必要な variant**:
  - `v-2309ee74af` assertion 暗号化を有効にした構成で、<saml:EncryptedAssertion> が <samlp:Response> 直下（平文 <saml:Assertion> と同じ位置）にある
  - `v-155212bcff` 同じ <Response> に平文 <Assertion> と <EncryptedAssertion> の両方が残っていない
- **対照（negative control）**:
  - ★ 受動規則として扱う。対象が実際に送出した該当要素ごとに検査し、当該 Run で 1 件も観測されなければ satisfied_with_note（観測機会なし）とする。**条件述語を置かない**: 肯定的な観測材料しか作れない CAPABILITY_BASED 述語では『機能を持たない』ことを FALSE にできず（申告のみの false は UNKNOWN）、非対応の対象が永久に not_verified になる。IIP-SSO01.er 等と同じ扱いに揃えた
  - ★ 訂正: 前版は条件なしで <EncryptedID> と <EncryptedAttribute> の位置も必須 variant にしていたが、IIP-IDP09.b は識別子・属性の暗号化を **OPTIONAL** としている。識別子・属性を暗号化しない適合 IdP を不適合または未検証にしてしまっていた。assertion（IIP-IDP09.a により対応必須）／識別子（IIP-SSO01.fd）／属性（IIP-SSO01.fe）に分けた
  - ★ 平文を残したまま暗号文を足す実装が典型的な違反
  - ★ IIP-SSO01.dm / .dn（@Type）と同じ節に由来するが、こちらは配置の規則
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fd</code> の詳細</summary>

- **必要な variant**:
  - `v-c0bcdccee7` 識別子暗号化を有効にした構成で、<saml:EncryptedID> が <saml:Subject> 直下（平文 <saml:NameID> と同じ位置）にある
  - `v-f46add9a1a` 同じ <Subject> に平文 <NameID> と <EncryptedID> の両方が残っていない
- **対照（negative control）**:
  - ★ 受動規則として扱う。対象が実際に送出した該当要素ごとに検査し、当該 Run で 1 件も観測されなければ satisfied_with_note（観測機会なし）とする。**条件述語を置かない**: 肯定的な観測材料しか作れない CAPABILITY_BASED 述語では『機能を持たない』ことを FALSE にできず（申告のみの false は UNKNOWN）、非対応の対象が永久に not_verified になる。IIP-SSO01.er 等と同じ扱いに揃えた
  - ★ IIP-IDP09.b により識別子の暗号化は OPTIONAL。暗号化しない対象では <EncryptedID> が観測されず satisfied_with_note になる
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fe</code> の詳細</summary>

- **必要な variant**:
  - `v-9d136b8b8f` 属性暗号化を有効にした構成で、<saml:EncryptedAttribute> が <saml:AttributeStatement> 直下（平文 <saml:Attribute> と同じ位置）にある
  - `v-6d76bf82e3` 同じ <AttributeStatement> に平文 <Attribute> と <EncryptedAttribute> の両方が残っていない
- **対照（negative control）**:
  - ★ 受動規則として扱う。対象が実際に送出した該当要素ごとに検査し、当該 Run で 1 件も観測されなければ satisfied_with_note（観測機会なし）とする。**条件述語を置かない**: 肯定的な観測材料しか作れない CAPABILITY_BASED 述語では『機能を持たない』ことを FALSE にできず（申告のみの false は UNKNOWN）、非対応の対象が永久に not_verified になる。IIP-SSO01.er 等と同じ扱いに揃えた
  - ★ IIP-IDP09.b により属性の暗号化は OPTIONAL。暗号化しない対象では <EncryptedAttribute> が観測されず satisfied_with_note になる
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.dr</code> の詳細</summary>

- **必要な variant**:
  - `v-d7023c4b52` 対象が上流 Samlier-IdP へ送る AuthnRequest の @ID が xs:ID の字句規則に適合する
  - `v-433b556e33` 連続する 2 回のプロキシで上流への @ID が毎回異なる
  - `v-6f88e2941e` 上流への @ID が、元要求（下流 Samlier-SP → 対象）の @ID をそのまま流用していない
- **対照（negative control）**:
  - ★ IIP-SSO01.af は role が sp（SP が生成する要求）だけを対象にしている。プロキシ IdP も上流へ新しい AuthnRequest を生成するので、その @ID にも同じ規則が適用される
  - ★ 『元要求の @ID をそのまま使う』実装が典型的な違反。別のデータオブジェクトに同じ識別子を割り当てている
  - ★ 確率・seed の細目は IIP-SSO01.cd / .ce / .cf が role idp/sp で覆う
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **注記**: SP 向けの無条件義務（IIP-SSO01.af）と、プロキシ IdP 向けの条件付き義務（本義務）に分けた。
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.bk</code> の詳細</summary>

- **必要な variant**:
  - `v-9e495172fb` IdP-initiated SSO で RelayState を含める構成が取れる（対応している場合）
  - `v-fe2226e920` 対応していない場合は NOT_SUPPORTED。適合違反ではない
- **対照（negative control）**:
  - ★ MAY_CLASS。RelayState を含めない IdP を FAIL にしてはならない
  - ★ 『RelayState が URL である』ことも MAY。URL 以外の不透明値でもよい
  - ★ 受け取った SP 側の扱いは IIP-SSO01.y2（既定の遷移先）と IIP-SSO01.ab（スキーム制限）
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.y2</code> の詳細</summary>

- **必要な variant**:
  - `v-a96365ada1` RelayState なしの unsolicited 応答 → 既定の遷移先に到達する（エラーにならない）
  - `v-bcabda0ff0` RelayState なしの unsolicited 応答を 2 回 → いずれも既定の遷移先に到達する
- **対照（negative control）**:
  - ★ SHOULD_CLASS。判定対象は『既定の遷移先を用意していること』だけ
  - ★ 訂正: 前版は『RelayState 付き → その URL に遷移する』を必須 variant に置いていたが、原文は RelayState を URL として扱うことを**相互合意に基づく MAY** としている（IIP-SSO01.bk）。URL へ遷移しない SP でも、既定の遷移先を用意していれば本 SHOULD は満たされる。verdict 対象から外した
  - ★ RelayState を URL として解釈する場合のスキーム制限は IIP-SSO01.ab（E90 が追加する新 §4.1.6）で判定する
  - ★ 既定の遷移先を持たない SP は、RelayState なしの unsolicited 応答でエラーになる。それが検出対象
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fl</code> の詳細</summary>

- **必要な variant**:
  - `v-320d838764` SP が送出する AuthnRequest で NameIDPolicy/@Format が transient ではなく、AllowCreate に固有の状態管理を行わない構成 → AllowCreate=true
  - `v-8fbb01ee1f` AllowCreate を同意・動的識別子作成等の特定用途に使う構成は本 SHOULD の対象外
- **対照（negative control）**:
  - ★ E14 は SAML2Prof 4.1.4.1 の旧 MUST を削除しただけでなく、Core 3.4.1.1 に新しい SHOULD を追加した
  - ★ 『特定用途に使わない』場合だけの SHOULD。常に true を強制してはならない
  - ★ NameIDPolicy/@Format=transient の送出物は本 SHOULD の実行時対象外で、IIP-SSO01.fn の MUST_NOT を適用する
  - ★ 当該 Run で transient 以外の NameIDPolicy 付き AuthnRequest が観測されなければ satisfied_with_note。グローバルな NOT_APPLICABLE にはせず、message ごとの実行時 scope とする
  - ★ proxy IdP が requester になる場合は IIP-SSO01.fm で分ける
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fm</code> の詳細</summary>

- **必要な variant**:
  - `v-0e619f74de` 対象が上流 IdP へ送る AuthnRequest で NameIDPolicy/@Format が transient ではなく、AllowCreate に固有の状態管理を行わない構成 → AllowCreate=true
- **対照（negative control）**:
  - ★ SP と proxy IdP では観測経路と適用条件が異なるため role を分離した
  - ★ 上流要求の NameIDPolicy/@Format=transient は本 SHOULD の実行時対象外で、IIP-SSO01.fo の MUST_NOT を適用する
  - ★ 当該 Run で transient 以外の上流 NameIDPolicy 付き AuthnRequest が観測されなければ satisfied_with_note
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fn</code> の詳細</summary>

- **必要な variant**:
  - `v-0bdca828da` NameIDPolicy/@Format=transient の AuthnRequest → @AllowCreate を含めない
- **対照（negative control）**:
  - ★ AllowCreate を一切送らない実装も適合する。persistent で AllowCreate を使うことを positive control として要求すると、原文の MUST NOT にない生成能力を足してしまう
  - ★ transient AuthnRequest を送出する観測機会がなければ satisfied_with_note とし、違反にも NOT_APPLICABLE にもしない
  - ★ AllowCreate は <NameIDPolicy>（AuthnRequest）の属性で assertion 自体には存在しない。MUST NOT は属性を使用する requester の送出物で判定し、assertion consumer に架空の属性処理義務を作らない
  - ★ @Format 省略時に IdP が transient を返したことを見て requester を遡及的に違反にしない。返却 Format は IdP の裁量であり、assertions issued with transient の文脈は IIP-SSO01.fp で処理する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fo</code> の詳細</summary>

- **必要な variant**:
  - `v-ba78efb751` 対象が上流へ送る AuthnRequest の NameIDPolicy/@Format=transient → @AllowCreate を含めない
- **対照（negative control）**:
  - ★ SP の送出要求は IIP-SSO01.fn で分ける
  - ★ persistent で AllowCreate を使う能力は本 MUST NOT の要件ではないため対照として要求しない
  - ★ @Format 省略時の上流 IdP の返却 Format を見て proxy requester を遡及的に違反にしない。assertions issued with transient の文脈は IIP-SSO01.fp で処理する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fp</code> の詳細</summary>

- **必要な variant**:
  - `v-ffd71f197b` Format=transient で AllowCreate=true / false / 省略の 3 要求 → AllowCreate の値だけを理由に成功・エラーを変えない
  - `v-e0c2235a54` @Format 省略等で結果の assertion が transient NameID を含む場合 → AllowCreate の値だけを理由に永続状態を作成・関連付けしない
- **対照（negative control）**:
  - ★ SHOULD_CLASS。要求側の MUST NOT（IIP-SSO01.fn）があっても、堅牢性のため受信側 SHOULD は独立して残る
  - ★ transient NameID の値は要求ごとに変わりうるため、値の一致や不透明性を本義務の判定に使わない
  - ★ 認証ポリシー等の別要因で結果が変わる場合は AllowCreate が原因と断定せず not_verified
  - ★ SHOULD ignore の主体は <NameIDPolicy>/@AllowCreate を処理する IdP。assertion 自体には同属性がないため、SP / 上流 assertion consumer に独立した『AllowCreate を無視する』義務を作らない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fr</code> の詳細</summary>

- **必要な variant**:
  - `v-02d1fe8dfb` Subject と異なる attesting entity を意図的に指定する構成 → SubjectConfirmation 内の BaseID / NameID / EncryptedID でその entity を識別する
  - `v-acfcc369c3` 通常の bearer SSO で subject 自身を attesting entity とする場合は観測機会なしとして記録する
- **対照（negative control）**:
  - ★ 『SP が assertion の宛先である』だけでは条件を満たさない。原文の entity は assertion を提示する attesting entity を指す
  - ★ Subject と異なる attesting entity を構成できない場合は satisfied_with_note。無理に SP entityID を要求しない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fs</code> の詳細</summary>

- **必要な variant**:
  - `v-08f910cfd4` 対象が送出した各 XML Signature に <ds:Object> がない
  - `v-64021b8bd9` 署名を送出しなかった role / Run は satisfied_with_note
- **対照（negative control）**:
  - ★ SHOULD_CLASS。Redirect binding のクエリ署名は XML Signature ではないので対象外
  - ★ 受信側の拒否義務は role ごとに IIP-SSO01.ft / .fu で分ける
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ft</code> の詳細</summary>

- **必要な variant**:
  - `v-48200d3166` 暗号学的には正しいが <ds:Object> を含む署名済み Response → 拒否される
  - `v-567d8f0ebc` 同じ条件の署名済み Assertion → 拒否される
  - `v-e09b2f9fb8` 対照: <ds:Object> だけを除いた同一内容・正しい署名 → 受理される
- **対照（negative control）**:
  - ★ ds:Object 内に攻撃文字列を置く必要はない。要素の存在だけで SHOULD reject
  - ★ Response / Assertion の双方を試す。片方だけでは verifier の全経路を覆わない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fu</code> の詳細</summary>

- **必要な variant**:
  - `v-d10cc24ad0` 暗号学的には正しいが <ds:Object> を含む署名済み AuthnRequest → 拒否される
  - `v-a30f713bf7` 対照: <ds:Object> だけを除いた同一内容・正しい署名 → 受理される
- **対照（negative control）**:
  - ★ Suite SP の署名鍵を対象に信頼させられない場合は not_verified(test_precondition_signing_key_not_trusted)
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fv</code> の詳細</summary>

- **必要な variant**:
  - `v-354efe3081` 対象が送出した CBC-mode <EncryptedAssertion> を含む Response → Response 要素に有効な XML Signature がある
  - `v-274e9c20de` CBC でない暗号化、または EncryptedAssertion がない送出物は本 SHOULD の対象外として記録する
- **対照（negative control）**:
  - ★ 実行時の受動規則なので capability predicate を置かない。CBC EncryptedAssertion が観測されなければ satisfied_with_note
  - ★ assertion 内部の署名だけでは暗号文を復号前に完全性検証できないため、Response 署名を見る
  - ★ 同じ規範句が Profile 4.1.3.5 と 4.1.4.3 の 2 か所に置かれているが、義務は二重計上しない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fw</code> の詳細</summary>

- **必要な variant**:
  - `v-4d08812217` 内部ポリシー・トレース・計装のいずれかで、CBC 暗号データの復号処理より前に外側の完全性保護を要求・検証することを確認する
  - `v-379c10445d` 補助証拠: CBC EncryptedAssertion を含み、暗号化層の外側に有効な完全性保護がない Response → 拒否される
  - `v-5dba8518cc` 補助証拠: CBC の EncryptedID / EncryptedAttribute を含み、その暗号化要素を覆う外側署名がない assertion → 拒否される
  - `v-cc77659329` 対照: 同じ暗号文を有効な Response / Assertion 署名で覆う → 受理される
- **対照（negative control）**:
  - ★ SHOULD_CLASS。拒否しなければ WARNING であって FAIL ではない
  - ★ 外部から『拒否された』だけでは処理順を証明できない。復号後に署名欠落を理由として拒否する誤実装も同じ結果になる。したがって verdict は内部ポリシー・トレース・計装に基づき、外部 fixture は補助証拠に限定する
  - ★ 内部証拠が得られなければ not_verified(processing_order_not_observable)
  - ★ 内側の平文 assertion 署名は復号後にしか検証できず、CBC oracle 緩和の『before processing encrypted data』を満たさない
  - ★ TLS を根拠にする場合は、暗号化データの asserting party を認証する層であることを示す必要がある。ブラウザ POST の UA-SP 間 TLS を自動的に IdP の完全性保護と見なさない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fx</code> の詳細</summary>

- **必要な variant**:
  - `v-854830820b` 対象が上流 IdP へ送る AuthnRequest → XML / binding 固有署名、または送信者認証・完全性保護された同期 binding のいずれかで保護される
- **対照（negative control）**:
  - ★ SP が生成する要求は IIP-SSO01.fj。proxy IdP は観測経路と条件が異なるため分離する
  - ★ 署名だけを要求しない。原文は binding による代替保護を選言で認める
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fy</code> の詳細</summary>

- **必要な variant**:
  - `v-ca2e805819` Assertion 署名だけを不正にし、Response 署名は付けない応答 → セッションを確立しない
  - `v-dabc7b436b` 妥当な Assertion と署名不正の Assertion を同一 Response に入れる → 不正側の属性・Subject に依拠しない
  - `v-59a074c1ad` 対照: 同一内容で Assertion 署名を正しくした応答 → 受理される
- **対照（negative control）**:
  - ★ IIP-SSO01.ar は Response 署名、本義務は Assertion 署名。Response だけを壊すケースでは Assertion 検証経路を証明できない
  - ★ IIP-SSO01.n（署名を検証する）とは別に、検証失敗後の非依拠を判定する
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.fz</code> の詳細</summary>

- **必要な variant**:
  - `v-ec84114455` secondary_peer の鍵で暗号学的には正しく署名し、Assertion/@Issuer は対象 IdP とした応答 → 不一致を検出して依拠しない
  - `v-a063eb7f53` Assertion 署名検証鍵を Issuer の信頼済みメタデータに結び付けていることを申告・設定証拠で確認する
- **対照（negative control）**:
  - ★ IIP-SSO01.at は Response 署名者、本義務は Assertion issuer。署名層が異なるため分離する
  - ★ 暗号学的な署名成功だけでは足りない。信頼済み issuer と鍵の対応を見る
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ga</code> の詳細</summary>

- **必要な variant**:
  - `v-6574c2cce0` 対象が申告した context 強度順で low < high を用意し、Comparison=minimum + low を要求 → 成功時は low 以上
  - `v-0aca5edddc` AuthnContextDeclRef を使う構成でも、成功時は同じ minimum 規則に従う
  - `v-ef2ec24bee` 満たせない場合のエラー Response は適合として記録する
- **対照（negative control）**:
  - ★ Core は context の強度を responder が判断するとする。Suite 独自の順序を課さず、対象が申告・設定した順序で比較する
  - ★ エラーも許されるので、成功時の結果だけを verdict 対象にする。常時エラーを本義務だけで違反にしない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gb</code> の詳細</summary>

- **必要な variant**:
  - `v-559c3ec2ff` 対象が申告した context 強度順で low < high を用意し、Comparison=better + low を要求 → 成功時は high 等、low より強い context
  - `v-a8ea010ef3` AuthnContextDeclRef を使う構成でも、成功時は同じ better 規則に従う
  - `v-3d023f4024` 満たせない場合のエラー Response は適合として記録する
- **対照（negative control）**:
  - ★ E45 は ordered-set 規則を削除せず、ordering が relevant な場合に条件化した。AuthnRequest では ordering が significant と明示されるため、候補の並びは preference 順として有意である
  - ★ ただし preference 順は context の強度順とは限らない。better の強度判定は responder の判断で行い、リスト順を Suite 独自の強度順に読み替えない
  - ★ 強度は responder の判断。Suite 独自の認証方式ランキングを足さない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gc</code> の詳細</summary>

- **必要な variant**:
  - `v-5048c9e341` 対象が申告した context 強度順で low < medium < high を用意し、利用可能な low / medium と Comparison=maximum + high を要求 → 成功時は medium（上限以下で可能な限り強い値）
  - `v-c1b446f0b5` AuthnContextDeclRef を使う構成でも、成功時は同じ maximum 規則に従う
  - `v-da7511ab89` 満たせない場合のエラー Response は適合として記録する
- **対照（negative control）**:
  - ★ 『maximum』を最も強い認証を返す意味に取り違えない。要求上限を超えない範囲で最大を選ぶ
  - ★ 単に上限を超えないだけでは不十分。low と medium の両方が利用可能なのに low を返す実装を検出する
  - ★ 強度は responder の判断なので、対象が申告・設定した順序を fixture に固定する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gd</code> の詳細</summary>

- **必要な variant**:
  - `v-b438f5cd45` 複数の attesting entity を許可する構成 → entity ごとに別の bearer <SubjectConfirmation> がある
  - `v-1815c770bd` 通常の単一 attesting entity の構成は観測機会なしとして記録する
- **対照（negative control）**:
  - ★ 複数 entity を 1 つの SubjectConfirmation 内に詰める構造を許さない
  - ★ 複数 attesting entity を構成できない対象に能力を要求する規則ではない
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.ge</code> の詳細</summary>

- **必要な variant**:
  - `v-ad420f4fa9` 対象と上流 Test IdP が共通に対応する version を複数設定 → 対象の上流 AuthnRequest は共通の最高 version
- **対照（negative control）**:
  - ★ SP requester は IIP-SSO01.fg。proxy IdP の上流要求は別の観測経路なので分離する
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gf</code> の詳細</summary>

- **必要な variant**:
  - `v-9568508321` 上流 responder の version 能力情報を与えない構成 → 対象の上流 AuthnRequest は対象自身の最高対応 version
- **対照（negative control）**:
  - ★ SP requester は IIP-SSO01.fh。metadata 等で能力が既知なら本 SHOULD の条件外
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gg</code> の詳細</summary>

- **必要な variant**:
  - `v-600cbb978f` 対象の対応 response version を制限 → 上流 AuthnRequest は対応外 response version に対応する version を使わない
- **対照（negative control）**:
  - ★ SP requester は IIP-SSO01.el。proxy IdP が生成する別メッセージも同じ規則に従う
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gh</code> の詳細</summary>

- **必要な variant**:
  - `v-b4764a1f1c` 対象が上流 AuthnRequest に同意取得を示す @Consent を入れる場合、XML または binding 固有署名がある
- **対照（negative control）**:
  - ★ SP の AuthnRequest は IIP-SSO01.am。proxy IdP が Consent を送らない場合は観測機会なし
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gi</code> の詳細</summary>

- **必要な variant**:
  - `v-1f1c29c9eb` @ID を欠くなど ID を特定できない不正 AuthnRequest → 対象が SAML Response を返す場合、その Response に @InResponseTo がない
  - `v-00998055ee` 対照: ID を特定できる妥当な AuthnRequest への Response → @InResponseTo があり一致する（IIP-SSO01.ap）
- **対照（negative control）**:
  - ★ IIP-SSO01.y は unsolicited Response、本義務は要求は届いたが ID を特定できない error path。別経路なので分離する
  - ★ 原文は Response を返すこと自体を要求していない。HTTP エラーや無応答なら本義務の違反にしない
- **参照先仕様**: `SAML2Prof#4.1`
- **source_clauses**: `[0, 125)` `sha256:ff1057626aaa…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SSO01.gj</code> の詳細</summary>

- **必要な variant**:
  - `v-9b81482bbc` 同じ強度ではない 2 つの satisfiable AuthnContextClassRef を [A, B] の順で要求 → 成功時は A を優先する
  - `v-556e2b444a` 同じ候補を [B, A] に反転 → 成功時は B を優先する
  - `v-c11826dbf2` AuthnContextDeclRef を複数指定して双方を満たせる構成でも、先頭を最優先として評価する
- **対照（negative control）**:
  - ★ E45 は旧来の無条件 ordered-set 文を削除したのではなく、『ordering が relevant な場合』に限定した。さらに AuthnRequest は ordering が significant な例として明示されているため、本 profile では MUST が適用される
  - ★ 候補を 1 件だけ送るケースでは順序を無視する実装を検出できない。順序反転の対照を必須にする
  - ★ 対象がいずれかの候補を満たせずエラーにする場合、順序評価の積極的証拠にならないため not_verified とする
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Prof#4.1`
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
  - `v-4013eedd13` verdict 対象: <saml:Subject> 付き AuthnRequest。SAML2Core 3.4.1.4 が一意の結果を規定する（返る Assertion の Subject が要求と strongly match する、または要求された主体を認識できなければ error <Status> を返す）
  - `v-77b59b3e0d` strong match — identifier: 要求 Subject の BaseID / NameID / EncryptedID と、応答 Subject の復号後 identifier の内容・全属性が一致する（NameIDPolicy が別 Format を指定した場合だけ、物理値は異なっても同一主体ならよい）
  - `v-e45043ebaf` strong match — encryption: 要求側と応答側のどちらか一方だけが EncryptedID でも、復号後の identifier が同一なら一致として扱う
  - `v-487d2712a0` strong match — confirmation: 要求 Subject に SubjectConfirmation が 1 件以上あれば、応答 Subject はその少なくとも 1 件の方式で確認可能な SubjectConfirmation を含む
  - `v-0b60ff9606` negative control: identifier の内容または属性が異なる／要求した確認方式を 1 件も満たさない Assertion を成功応答として返す → violated
  - `v-b7935a4c4d` 情報記録のみ: <saml:Conditions>（SAML2Core 3.4.1: 'The responder MAY modify or supplement this set as it deems necessary'）
  - `v-c019bae96f` 対象外（取り込まれた SAML2Core の規則が扱う）: <Scoping>/@ProxyCount と <IDPList> — E65 反映後の ProxyCount=0 エラー処理と proxying 規則を IIP-SSO01.aw〜.bd で判定する。ただしそれらは『プロキシする場合』が条件で、プロキシしない IdP が Scoping を無視することは適合
  - `v-64c97f0648` 情報記録のみ: <RequesterID>（SAML2Core 3.4.1.2 に処理規則の記述がなく、3.4.1.2 の <IDPList> 検査は 'the intermediary MAY examine the list and return ...' の二択）
  - `v-912088ec9a` 情報記録のみ: 無効な AssertionConsumerServiceIndex（SAML2Core 3.4.1: 'MAY return an error <Response> or it MAY use the default location' — 二択が明示）
  - `v-67f70ac118` 情報記録のみ: ProviderName / Consent — SAML2Core に処理規則の記述がない
  - `v-6f10b7d2ad` 対象外（他義務が具体的処理規則を扱う）: <NameIDPolicy>→IIP-IDP10・IIP-SSO01.fl〜.fp / <RequestedAuthnContext> exact→IIP-IDP08、minimum/better/maximum→IIP-SSO01.ga〜.gc、候補順→.gj / ForceAuthn→IIP-IDP06 / IsPassive→IIP-IDP07 / AssertionConsumerServiceURL・ProtocolBinding・AssertionConsumerServiceIndex→IIP-IDP12 / AttributeConsumingServiceIndex→IIP-IDP04.b / <Extensions>・<Advice>→IIP-EXT01
- **対照（negative control）**:
  - 判定規則: SAML2Core が一意の結果（エラー XOR 無視）を規定する要素のみ verdict 対象にする。二択が許される要素は verdict を付けず情報記録のみとする
  - ★ 訂正: 前版は <Scoping> / ProxyCount / <IDPList> をまとめて『二択なので情報記録のみ』としていたが、SAML2Core 3.4.1.5.1 には ProxyCount と IDPList について明確な MUST NOT / MUST がある。取り込み句（SAML2Prof 4.1.3.3）経由で IIP-SSO01.aw〜.bd に分解した
  - ★ 横断調査の結論: AuthnRequest の任意コンテンツのうち、IIP の他要件も取り込み句由来の義務も call out していないものは <saml:Conditions> / <RequesterID> / ProviderName / Consent であり、いずれも一意の処理規則を持たない。一意の処理規則を持つ <saml:Subject> だけが本義務の verdict 対象になる
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
| `IIP-SP04.a` | MUST | sp | `BROWSER` | — | full | Service Provider として IdP Discovery のリダイレクトプロトコルを一連で処理できる |
| `IIP-SP04.b` | MUST | sp | `BROWSER` | — | full | UA を HTTP GET で Discovery Service へリダイレクトして Discovery Protocol を開始する |
| `IIP-SP04.c` | MUST | sp | `BROWSER` | — | full | 少なくとも single-selection の Discovery Service policy 値に対応する |
| `IIP-SP04.d` | MUST | sp | `BROWSER` | — | full | すべての Discovery request に SP の entityID パラメータを含める |
| `IIP-SP04.e` | MUST | sp | `BROWSER` | — | full | Discovery request の entityID パラメータを URL encode する |
| `IIP-SP04.f` | MUST_NOT | sp | `BROWSER` | — | full | return URL の query に返却 IdP 用の実効パラメータ名をあらかじめ含めない |
| `IIP-SP04.g` | MUST | sp | `BROWSER` | — | full | 各 Discovery request で return を含めるか、metadata の既定 DiscoveryResponse endpoint を使う |
| `IIP-SP04.h` | MUST | sp | `AUTOMATED` | — | full | DiscoveryResponse metadata extension を公開する場合は @Binding を IdP Discovery Protocol URI に設定する |
| `IIP-SP04.i` | MUST | sp | `AUTOMATED` | — | full | 公開する各 DiscoveryResponse metadata extension を IdPDisco 定義の md:IndexedEndpointType 構造にする |

<details><summary><code>IIP-SP04.a</code> の詳細</summary>

- **必要な variant**:
  - `v-30f2c7f165` Suite が Discovery Service を演じる。対象 SP が UA を DS へリダイレクトし、DS が返した選択済み IdP の entityID を受けて SSO を継続できる
  - `v-a45da3e7a4` 返却 URL に実効 returnIDParam 名の parameter が存在しない場合、それを選択済み IdP の識別子が返ったものとして扱わない
- **対照（negative control）**:
  - 成功結果と空の結果を対にする。成功だけでは返却パラメータを読まず既定 IdP を使う実装を検出できない
  - 選択結果がない後の振る舞い（製品自身の IdP 選択 UI、既定 IdP への遷移、エラー表示等）は IdPDisco が規定していないため verdict 対象にしない
  - IdPDisco section 2 の DS 主体の MUST/SHOULD（isPassive 時の UI、返送方法、metadata 照合等）は Test Peer である Suite 側の fixture 規則であり、対象 SP の義務にはしない
  - return / policy / returnIDParam / isPassive の MAY は利用許可であって全機能の提供能力を SP に要求しない。実際に選んだ経路へ適用される MUST/MUST NOT だけを .b〜.i で判定する
- **参照先仕様**: `IdPDisco`
- **注記**: 非規範の IIP 注記により、製品固有の discovery UI の実装までは要求されず、IdPDisco の単純なリダイレクト規約への対応が対象である。IdPDisco の SP 向け規範内容は .b〜.i に分解した。Discovery Service 主体の規範文と、SP が使ってよい任意パラメータは対象 SP の独立義務にしていない。IIP 注記の『discovery mechanisms SHOULD use SAML metadata…』もイタリック＝非規範なので、独立義務にしない。
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.b</code> の詳細</summary>

- **必要な variant**:
  - `v-4e779bac55` Discovery を開始させ、UA が DS endpoint へ HTTP GET request を送ることを Transcript で確認
- **対照（negative control）**:
  - SP から DS への message exchange の transport だけを判定する。DS から SP への HTTP GET は DS 主体なので Suite fixture の自己検証に置く
  - IdPDisco はこの箇所で redirect status code を固定していないため、特定の 3xx code を独自の verdict 条件にしない
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.c</code> の詳細</summary>

- **必要な variant**:
  - `v-c7521e55ac` policy を省略して既定の single を使う、または policy=urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol:single を指定し、単一 IdP の選択結果を処理できる
- **対照（negative control）**:
  - 省略時の既定値と明示指定の両方を能力として要求しない。少なくとも一方の経路で single policy を処理できればよい
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.d</code> の詳細</summary>

- **必要な variant**:
  - `v-1e263870b9` DS が受信した query に entityID parameter が存在し、その値が対象 SP の entityID と一致する
- **対照（negative control）**:
  - パラメータ名だけでなく値も照合する。固定ダミー値を送る実装を通さない
  - 原文はこの箇所で cardinality を明記していないため、同名 parameter が厳密に 1 件であることを独自の verdict 条件にしない
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.e</code> の詳細</summary>

- **必要な variant**:
  - `v-5f5f709085` query delimiter（例: &）を値に含む Test SP entityID を使い、生の query で delimiter が percent-encoding され、1 回の decode で元の entityID になる
- **対照（negative control）**:
  - パース後に再構成した query では判定しない。ブラウザが受け取った Location の生 query component を記録して検査する
  - delimiter を含む entityID を設定できない場合は、percent-encoding の有無を識別できる別の文字（例: %, 非 ASCII）へフォールバックする。それも設定できなければ NOT_VERIFIED(entityid_encoding_probe_unavailable) とし、対象の違反にしない
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.f</code> の詳細</summary>

- **必要な variant**:
  - `v-33c2670d12` 対象が送出した各 request について実効 returnIDParam を求め、return URL の既存 query に同名 parameter がないことを検査
- **対照（negative control）**:
  - custom returnIDParam の利用能力は MAY なので要求しない。観測された request ごとに、明示値または既定の entityID を使って判定する
  - return parameter 自体が観測されない場合は satisfied_with_note。NOT_APPLICABLE にして Run 全体から除外しない
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.g</code> の詳細</summary>

- **必要な variant**:
  - `v-9a011b6e38` 対象が送出した各 Discovery request について、return が存在する、または return 省略時に SP metadata の実効 default DiscoveryResponse endpoint が返却先として使えることを確認
- **対照（negative control）**:
  - message 単位の選言として評価する: return あり → satisfied / return なし・実効 default metadata endpoint あり → satisfied / どちらもなし → violated / metadata の対応を確認不能 → not_verified(metadata_return_basis_undetermined)
  - 『metadata を使わない場合』は request ごとの実行時 scope であり、製品全体の condition predicate にしない。metadata なしの構成を提供する能力も要求しない
  - Discovery request 自体を 1 件も観測できなければ NOT_VERIFIED(no_discovery_request_observed)。satisfied_with_note で WARNING を発生させない
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.h</code> の詳細</summary>

- **必要な variant**:
  - `v-2e03863d4d` 公開 metadata 内の各 idpdisc:DiscoveryResponse/@Binding が urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol と一致する
- **対照（negative control）**:
  - DiscoveryResponse extension 自体の公開は任意。1 件も観測されない場合は satisfied_with_note とし、NOT_APPLICABLE にはしない
  - Location / index / isDefault は md:IndexedEndpointType の一般規則だが、本義務の参照句が追加で固定するのは Binding 値だけである
- **参照先仕様**: `IdPDisco`
- **source_clauses**: `[0, 75)` `sha256:ce8bdccd17ea…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP04.i</code> の詳細</summary>

- **必要な variant**:
  - `v-f158b81008` 公開 metadata の各 idpdisc:DiscoveryResponse が同梱 schema に適合し、md:IndexedEndpointType の必須構造（Location / index / Binding）を持つ
- **対照（negative control）**:
  - DiscoveryResponse extension 自体の公開は任意。1 件も観測されない場合は satisfied_with_note とし、NOT_APPLICABLE にはしない
  - Binding の固定 URI は .h で別に判定する。本義務は型・必須属性・XML 構造を扱う
  - SPSSODescriptor/Extensions への配置は、IdPDisco §2.5 では DS の SHOULD に含まれる説明であり、本義務の独立 verdict 条件へ引き上げない
- **参照先仕様**: `IdPDisco`
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
| `IIP-SP12.a` | MUST_NOT | sp | `CONFIG` | — | core | persistent NameID の構造・内容に、SAML2Core 8.3.7 を超える要求を課してはならない |
| `IIP-SP12.b` | MUST_NOT | sp | `ATTESTED` | — | core | 設定・配備文書の上でも、persistent NameID に §8.3.7 を超える構造・意味を要求しない |

<details><summary><code>IIP-SP12.a</code> の詳細</summary>

- **必要な variant**:
  - `v-a2762edca6` 【前提】未知の主体を自動的に受け入れる設定（自動プロビジョニング / JIT）にしたうえで実行する
  - `v-85eb163259` 新規主体 A に不透明な擬似乱数値（英数 32 文字）→ 受理される
  - `v-10a9719c14` 新規主体 B に長さ 1 コードポイントの値 → 受理される
  - `v-2a577559f7` 新規主体 C に長さ 256 コードポイントの値 → 受理される
  - `v-8e34c9b1f4` 新規主体 D に区切り記号（@ / = / :）を含む値 → 受理される
  - `v-0e73bc8b6f` 新規主体 E に区切り記号を一切含まない値 → 受理される
  - `v-c25b387f84` 新規主体 F に SPNameQualifier を省略した NameID → 受理される（§8.3.7 で省略が許されている）
  - `v-ca630ac6b1` 対照: §8.3.7 に適合しない値（257 コードポイント）→ 拒否してよい。これを FAIL にしない
  - `v-683419f892` 対照: 自動プロビジョニングを切った構成で新規主体 → 拒否される。これも FAIL にしない
- **対照（negative control）**:
  - ★ 訂正: 前版は『§8.3.7 に適合する任意の値を受理する』としていたが、原文は『NameID に §8.3.7 を超える意味・構造を要求してはならない』であって、未知の主体・未プロビジョニング・アカウント連携ポリシーなど**構造以外の正当な理由による拒否まで禁じてはいない**
  - ★ したがって『自動プロビジョニングが有効で、任意の新規主体を受け入れられる』ことをテスト前提にする。前提を満たせない場合は not_verified(provisioning_precondition_unmet)。対象の不適合ではない
  - ★ 訂正: 前版は『同一主体に別の不透明値を発行する』variant を置いていたが、これは persistent identifier の永続性そのものを壊す。値を変えるときは**別主体または別 IdP**を使う
  - ★ 拒否理由が NameID の構造だと特定できなければ NOT_VERIFIED にする。属性は全 variant で固定し、変える値は NameID だけにする。エラー画面の文言・監査ログ・対象の申告のいずれかで理由を特定できたときだけ violated にできる
  - ★ 『メールアドレス形式でないと動かない SP』はこの義務の典型的な違反であり、mutant SUT の候補にする
- **設定不能時の意味**: `test_precondition`
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: 訂正 1: 前版はこの義務を NOT_OBSERVABLE としていたが、§8.3.7 は persistent 識別子の値空間を規定しており、『構造を理由に範囲を狭めていないか』は観測できる。訂正 2: ただし観測できるのは『構造を理由とする拒否』だけで、『任意の値を必ず受理する』ことまでは原文が要求していない。テスト前提（プロビジョニング設定）が要るため testability は CONFIG とし、前提を満たせない場合は違反ではなく not_verified とする。設定・配備文書の上での要求は IIP-SP12.b（申告）で扱う。
- **source_clauses**: `[0, 220)` `sha256:4487b42c2037…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-SP12.b</code> の詳細</summary>

- **必要な variant**:
  - `v-c4fc34691d` persistent NameID の形式・接頭辞・正規表現を必須設定にしていないことを申告で確認する
  - `v-79788277d5` NameID を主キーやメールアドレスとして扱う前提がないことを申告で確認する
  - `v-bda0025d23` NameID の値から所属・権限などの意味を読み取る処理がないことを申告で確認する
- **対照（negative control）**:
  - ★ IIP-SP12.a で観測できるのは『Suite が送った値の集合』に対する挙動だけ。対象が特定の形式を必須設定として要求している場合、その設定を満たす値しか試していない可能性がある
  - ★ 申告が IIP-SP12.a の観測と矛盾したら INCONSISTENT（申告では要求なし、実際は構造を理由に拒否）
  - ★ 『NameID を主キーに使っている』こと自体は違反ではない。違反になるのは『NameID がある形式であることを要求する』場合に限る
- **参照先仕様**: `SAML2Core#8.3.7`
- **注記**: IIP-SP12.a（観測）と本義務（申告）は同じ原文句の別側面。証拠ラダー上、a の自動観測のほうが強い。
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
  - `v-3333f2d975` 満たせない RequestedAuthnContext → 最上位 Responder のエラー Response（二次 NoAuthnContext は MAY）
  - `v-83cbd6227b` IsPassive でセッションなし → エラー Response（二次 NoPassive は MAY）
- **対照（negative control）**:
  - 未登録 ACS URL を FAIL 条件に使ってはならない。その場合『acceptable location』が既知でなく、エラー Response を返さないことが原文で許される
  - ★ 二次 StatusCode は E65 や Core の各処理規則で MAY。特定の二次値を必須にしない
- **参照先仕様**: `SAML2Core`
- **注記**: acceptability の基準は原文で形式化されておらず IdP のポリシー次第と明記されている。
- **source_clauses**: `[0, 258)` `sha256:4b26a1ff778d…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP06

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP06) ／ 節ダイジェスト `sha256:9f0c9ea1d83d…` ／ 節長 925 ／ 非規範スパン 2

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP06.a` | MUST | idp | `BROWSER` | — | core | ForceAuthn=true のとき、既存のセキュリティコンテキストに依拠せず直接認証する |
| `IIP-IDP06.b` | MUST | idp | `ATTESTED` | — | core | 実装内の認証機構が ForceAuthn の指示にアクセスでき、その値によって動作を変えられる |
| `IIP-IDP06.c` | MUST_NOT | idp | `BROWSER` | — | core | ForceAuthn と IsPassive がともに true のとき、IsPassive の制約を満たせない限り新規認証を行ってはならない |

<details><summary><code>IIP-IDP06.a</code> の詳細</summary>

- **必要な variant**:
  - `v-2aa2a2feb5` セッション確立後に ForceAuthn=true → 新たな認証操作が行われる
  - `v-b50bafe8e2` 返る Assertion の AuthnStatement/@AuthnInstant が AuthnRequest の IssueInstant 以降になっている
  - `v-cbbf766efc` 対照: ForceAuthn を省略（既定 false）+ 既存セッションあり → 既存コンテキストの再利用が許される。これを FAIL にしない
  - `v-782a8c9281` 対照: ForceAuthn=false を明示 + 既存セッションあり → 同上
- **対照（negative control）**:
  - ★ 禁じられているのは true のときに既存コンテキストへ依拠すること。false / 省略時に自主的に再認証することは禁止されていない。『false なら必ず再利用する』を期待するケースを作ると、常に再認証する適合実装を FAIL にする
  - ★ AuthnInstant だけでは弱い。既存セッションが有効な状態で始めること（そうでないと ForceAuthn の効果と区別できない）
  - ★ SAML2Prof 4.1.3.4 も同旨を述べるが、IIP-IDP06 が参照するのは [SAML2Core] なので根拠は 3.4.1 に置く
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: 『If a value is not provided, the default is "false"』は既定値の定義であって IdP への義務ではない。IsPassive と併用したときの MUST NOT は IIP-IDP06.c に分けた。
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

<details><summary><code>IIP-IDP06.c</code> の詳細</summary>

- **必要な variant**:
  - `v-cf2b27728d` ForceAuthn=true + IsPassive=true + 対話的な認証しか使えない構成 → 利用者に可視の認証画面を出さず、エラー <Status> を持つ <Response> を返す
  - `v-c7af3cd4e0` 対照: ForceAuthn=true + IsPassive=false → 可視の再認証が起きてよい（これを FAIL にしない）
  - `v-026e724e21` 対照: ForceAuthn=true + IsPassive=true + 非対話の認証機構（証明書 / Kerberos 等）が使える構成 → 新規認証してよい
- **対照（negative control）**:
  - ★ 『ForceAuthn=true なら必ず再認証』と実装したケースは、この MUST NOT を検出できない。IsPassive との組合せを必ず対にする
  - ★ 二次 status code は MAY なので、NoPassive が返ることを判定条件にしない（エラー <Status> であれば足りる）
  - ★ 非対話の認証機構を持つ対象では条件が変わる。preflight で対象の認証方式を申告させ、期待値を切り替える
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: IIP-IDP07（IsPassive）とは別の義務。あちらは IsPassive 単独の挙動、こちらは 2 属性の組合せ時の禁止。
- **source_clauses**: `[0, 116)` `sha256:40cd2c53a3cb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

#### IIP-IDP07

[原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-IDP07) ／ 節ダイジェスト `sha256:50adf1500e90…` ／ 節長 115 ／ 非規範スパン 0

| 義務 | Level | Role | Testability | 条件 | Core/Full | 要約 |
|---|---|---|---|---|---|---|
| `IIP-IDP07.a` | MUST_NOT | idp | `BROWSER` | — | core | IsPassive=true のとき、UI を可視的に奪って利用者と目立つやり取りをしてはならない |

<details><summary><code>IIP-IDP07.a</code> の詳細</summary>

- **必要な variant**:
  - `v-f3d6d26138` 既存セッションあり + IsPassive=true → 利用者操作なしで Assertion が返る（可視の画面遷移が発生しない）
  - `v-ff14535163` 既存セッションなし + IsPassive=true → 認証画面・同意画面が一切出ない（判定対象はここ）
  - `v-e4bf92c122` 同上のとき返る応答が、エラー <Status> を持つ <Response> であること（判定は IIP-IDP05.a。ここでは観測のみ記録する）
  - `v-bc823d23ce` 対照: 既存セッションなし + IsPassive 省略（既定 false）→ 認証画面が出てよい。これを FAIL にしない
  - `v-0aa2854948` 同意画面・属性リリース画面も『目立つやり取り』に含まれる（IsPassive=true では出さない）
- **対照（negative control）**:
  - ★ 2 状態（セッションあり／なし）の対照が必須。片方だけでは対応を証明できない
  - ★ 訂正: 前版は『セッションなし + IsPassive=true → NoPassive エラー』を必須 variant にしていたが、二次 status code は SAML2Core 3.4.1.4 で MAY。NoPassive が返らないことを FAIL にしてはならない。判定は『エラー <Status> を持つ <Response> が返る』かつ『可視の画面が出ない』まで
  - ★ 『可視の UI 奪取』は Suite が直接観測する必要がある。ブラウザ自動化で中間ページ・フォーム表示の有無を記録する。リダイレクトが挟まっても、利用者の操作を要求しなければ違反ではない
  - ★ 原文の名宛人は identity provider と user agent の両方だが、適合試験の対象は IdP 側だけである
  - ★ 『エラー <Response> を返すこと』は SAML2Core 3.4.1.4 の一般規則であり、IIP では IIP-IDP05.a が持つ。ここで二重に判定しない
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: 『If a value is not provided, the default is "false"』は既定値の定義であって義務ではない。ForceAuthn と併用したときの禁止は IIP-IDP06.c で扱う。
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
  - `v-e8a969d40b` 満たせる DeclRef → 一致した AuthnContextDeclRef に対応する認証コンテキストが返る
  - `v-a0addc6e54` 満たせない ClassRef / DeclRef → 最上位 StatusCode=Responder のエラー Response。二次 NoAuthnContext は任意
- **対照（negative control）**:
  - 満たせる／満たせないの対照が必須
  - ★ Errata E65 により NoAuthnContext は MAY。二次 code の省略を FAIL にしない
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
| `IIP-IDP10.a` | MUST | idp | `BROWSER` | — | core | samlp:NameIDPolicy 要素と Format / SPNameQualifier / AllowCreate 属性を受理して処理する |
| `IIP-IDP10.b` | MUST | idp | `BROWSER` | — | core | NameIDPolicy の内容を理解できない・受け入れられない場合、エラー <Status> を持つ <Response> を返す |
| `IIP-IDP10.c` | MUST | idp | `BROWSER` | `supports_encrypted_nameid`<br>(CAPABILITY_BASED) | core | Format が ...nameid-format:encrypted のとき、結果の assertion は平文でなく <EncryptedID> を含む |
| `IIP-IDP10.d` | MUST | idp | `BROWSER` | — | core | NameIDPolicy を受理した場合、返す識別子はその内容に従う（従えないならエラーを返す） |

<details><summary><code>IIP-IDP10.a</code> の詳細</summary>

- **必要な variant**:
  - `v-84d7c446ee` Format=persistent を指定 → 要求を処理できる（結果の検査は IIP-IDP10.d）
  - `v-526e67bb48` Format=transient を指定 → 要求を処理できる
  - `v-7f2708abf7` Format 省略 → IdP が任意の識別子を返してよい（エラーにしない）
  - `v-a76b85ca08` Format=urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified → 同上
  - `v-8d456513fa` SPNameQualifier を指定 → 要求を処理できる（結果の検査は IIP-IDP10.d）
  - `v-b23e712354` AllowCreate=true を指定 → 要求を処理できる
  - `v-0dc8b2fa76` AllowCreate=false を指定 → 要求を処理できる
  - `v-791e386992` AllowCreate 省略 → 要求を処理できる
  - `v-44e9f425df` NameIDPolicy 要素そのものを省略 → 要求を処理できる（IIP-SP03.a の生成側と対になる）
- **対照（negative control）**:
  - ★ AllowCreate=false を『新規識別子を絶対に作らない』と解釈して FAIL にしてはならない。原文に IdP への MUST はなく、SAML2Errata E14 は『the requester tries to constrain』『does not prevent the identity provider from assuming such information exists』と明示的に緩和している
  - ★ 『処理できる』の判定は『SAML として妥当な応答（成功またはエラー <Status>）が返る』こと。どの識別子を返すかの正しさは IIP-IDP10.d、エラーの出し方は IIP-IDP10.b で見る
  - ★ Format=unspecified / 省略のときに特定 Format を強制する検査を書かない（原文は『free to return any kind of identifier』）
- **参照先仕様**: `SAML2Core#3.4.1.1`
- **注記**: SAML2Errata E14 は AllowCreate の意味を書き換えており、SAML2Prof 4.1.4.1 から旧 MUST を削除して [SAMLCore] に委ねている。IIP-IDP10 単体は Errata の取り込みを明記しないため、ここでは OS 版 Core の基本処理を扱う。ただし IIP-SSO01 が Web Browser SSO Profile を Errata 反映込みで要求するので、E14 の追加規則は IIP-SSO01.fl〜.fp で規範義務として扱う。advisory には降格しない。
- **source_clauses**: `[0, 124)` `sha256:02dce7a982bb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP10.b</code> の詳細</summary>

- **必要な variant**:
  - `v-80490b1670` 対象が対応していない Format（例: 未知の URI）を指定 → エラー <Status> を持つ <Response> が返る
  - `v-d1f65d7c1a` 対象の知らない SPNameQualifier（別 SP の entityID）を指定 → 成功するかエラー <Status> を返すかのいずれかで、無言で無視しない
  - `v-8cd04b82cd` 対照: 対応している Format を指定 → 成功応答が返る（すべてエラーにする実装を落とす）
- **対照（negative control）**:
  - ★ 二次 status code InvalidNameIDPolicy は MAY。返らないことを FAIL にしない
  - ★ 『無言で別の Format を返す』は本義務の違反（受理も拒否もしていない）。IIP-IDP10.d と対で検査する
  - ★ negative control 必須: 対応 Format でも常にエラーを返す実装は本義務を『満たす』ように見えるが、IIP-IDP10.a の成功ケースで落とす
- **参照先仕様**: `SAML2Core#3.4.1.1`
- **source_clauses**: `[0, 124)` `sha256:02dce7a982bb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP10.c</code> の詳細</summary>

- **必要な variant**:
  - `v-07e998bf8f` Format=urn:oasis:names:tc:SAML:2.0:nameid-format:encrypted → Subject に <saml:EncryptedID> があり <saml:NameID> の平文がない
  - `v-4c05da8507` 復号すると、下地の識別子は IdP が対応する任意の型でよい（型を限定して FAIL にしない）
- **対照（negative control）**:
  - ★ 識別子の暗号化は IIP-IDP09.b で OPTIONAL。非対応なら条件が偽で NOT_APPLICABLE であり、その場合に期待されるのは IIP-IDP10.b のエラー応答である
  - ★ Format に関わらず IdP が自らのポリシーで <EncryptedID> を返すことは MAY として許されている（IIP-IDP10.d の対照に注意）
- **参照先仕様**: `SAML2Core#3.4.1.1`
- **source_clauses**: `[0, 124)` `sha256:02dce7a982bb…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP10.d</code> の詳細</summary>

- **必要な variant**:
  - `v-841e1ccc8c` Format=persistent を要求 → 返る <NameID>/@Format が要求値と一致する
  - `v-9198552197` Format=transient を要求 → 返る <NameID>/@Format が要求値と一致する
  - `v-c601eea237` SPNameQualifier を指定 → 返る <NameID>/@SPNameQualifier が指定値と一致する
  - `v-6b6e41be77` 対照: 要求と異なる Format を無言で返す実装を検出できる（成功応答なのに Format が違う）
  - `v-393e8c2dfa` 対照: Format 省略・unspecified・encrypted のときは本義務の対象外（任意の識別子でよい）
- **対照（negative control）**:
  - ★ 検出力の要: 『成功応答が返った』だけを見るケースでは、要求を無視して既定 Format を返す実装を素通しする
  - ★ 適用範囲から unspecified と encrypted を除く（原文が『free to return any kind of identifier』と述べる範囲）
  - ★ 訂正: 前版は根拠を §3.4.1.1 の『理解できない・受理できないならエラー』に置いていたが、その MUST は『受理したなら従う』までは含まない（受理可能と判断しつつ別の Format を返す余地が残る）。根拠を §3.4.1.4 の『assertions that meet the specifications defined by the request』に置き直した
  - ★ SAML2Errata E15 は同じ結論を明文化しているが、IIP-IDP10 は [SAML2Core] を errata 込みで参照していないため判定の根拠にはしない（advisory として記録する）
  - ★ IdP がポリシーで <EncryptedID> を返した場合は復号後の Format で比較する
- **参照先仕様**: `SAML2Core#3.4.1.4`
- **注記**: errata の適用方針（IIP が取り込みを明記した箇所だけ規範として扱う）に従い、E15 のみに依拠する規則は作らない。本義務は OS 版 §3.4.1.4 だけから導いている。
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
| `IIP-IDP12.a` | MUST | idp | `BROWSER` | — | core | AuthnRequest の AssertionConsumerServiceIndex により応答先を特定できる |
| `IIP-IDP12.e` | MUST | idp | `BROWSER` | — | core | AuthnRequest の AssertionConsumerServiceURL により応答先を特定できる |
| `IIP-IDP12.f` | MUST | idp | `BROWSER` | — | core | AuthnRequest の ProtocolBinding により返送バインディングを特定できる |
| `IIP-IDP12.b` | MUST | idp | `BROWSER` | — | core | AssertionConsumerServiceURL / Index が実際に要求元に紐づくことを、信頼できる手段で確認する |
| `IIP-IDP12.c` | MUST | idp | `BROWSER` | — | core | AssertionConsumerServiceIndex が省略された場合、要求元の既定ロケーションに <Response> を返す |
| `IIP-IDP12.d` | MAY | idp | `BROWSER` | — | full | 指定された index が無効な場合、エラー <Response> を返してもよいし、既定ロケーションを使ってもよい |

<details><summary><code>IIP-IDP12.a</code> の詳細</summary>

- **必要な variant**:
  - `v-8a720f637d` AssertionConsumerServiceIndex を指定 → メタデータの当該 index の ACS に <Response> が返る
  - `v-f1489d1e17` 既定でない index を指定 → 既定 ACS ではなくその index の ACS に返る（既定に固定する実装を落とす）
  - `v-e098afa042` 対照: 3 属性をすべて省略 → 既定 ACS に返る（IIP-IDP12.c）
- **対照（negative control）**:
  - ★ ACS を 2 つ以上持つ Test Peer メタデータでないと検出力がない（既定に固定する実装と区別できない）
  - ★ 『既定でない index』を必ず含める。既定 index だけを試すと、index を読まない実装と区別できない
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: 原文は 3 属性を列挙しているので、属性ごとに義務を分けた: Index → .a / URL → .e / ProtocolBinding → .f。属性ごとに検出力の作り方が違い、特に ProtocolBinding は積極的証拠が得られないことがあるため、1 義務にまとめると『検証できた属性』と『できなかった属性』を区別できない。値の検証義務は IIP-IDP12.b、既定ロケーションへの返送は IIP-IDP12.c、無効 index の扱いは IIP-IDP12.d。
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP12.e</code> の詳細</summary>

- **必要な variant**:
  - `v-783d022276` メタデータにある既定でない ACS URL を指定 → その URL に <Response> が返る
  - `v-7994c995f9` ProtocolBinding を伴わずに URL だけを指定 → その URL に返る（URL 単独で効くこと）
  - `v-d3e44c643c` 対照: URL を省略 → 既定 ACS に返る（IIP-IDP12.c）
- **対照（negative control）**:
  - ★ 既定 ACS の URL を指定しても検出力がない。必ず既定でない ACS を指す
  - ★ メタデータにない URL を指定したときの挙動は IIP-IDP12.b（検証義務）で扱う。ここでは扱わない
- **参照先仕様**: `SAML2Core#3.4.1`
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP12.f</code> の詳細</summary>

- **必要な variant**:
  - `v-12255e19c1` 【積極的証拠 A】ProtocolBinding を HTTP-POST と HTTP-Artifact で切り替える → 返送 binding が切り替わる（対象が Artifact に対応している場合）
  - `v-783e59a0c6` 【積極的証拠 B】応答に使えない binding（HTTP-Redirect / 未定義の binding URI）を指定 → エラー <Status>（例: 二次コード urn:oasis:names:tc:SAML:2.0:status:UnsupportedBinding）が返る
  - `v-291e65c2b4` ProtocolBinding=HTTP-POST を指定 → POST で返る（積極的証拠にはならない。既定が POST のため）
  - `v-6bffc31fab` ProtocolBinding=HTTP-Redirect を指定 → <Response> を HTTP-Redirect では返さない（これも単独では積極的証拠にならない。IIP-SSO01.x により Redirect は使えないため）
- **対照（negative control）**:
  - ★ 検出力の要: 『POST 指定 → POST で返る』と『Redirect 指定 → Redirect で返らない』は、**ProtocolBinding を一切読まずに常に既定 POST で返す実装でも両方通過する**。この 2 つだけで satisfied にしてはならない
  - ★ satisfied にできるのは、積極的証拠 A または B のいずれかが得られたときだけ。どちらも得られない場合は not_verified(no_positive_evidence_for_protocol_binding)
  - ★ 未対応 binding に対して黙って別の binding にフォールバックした場合、属性を処理したのか無視したのかを区別できない。これも not_verified とする
  - ★ 二次 status code は SAML2Core 3.4.1.4 で MAY なので、UnsupportedBinding が返らないこと自体は違反にしない。『エラー <Status> が返る』ことが証拠 B の条件
  - ★ SAML2Core 3.4.1 は Index と URL / ProtocolBinding の相互排他を述べるが、これは要求側（SP）への制約であり RFC2119 キーワードを持たないため IdP 側の義務は起こさない。両方を含む要求への挙動は advisory に記録する
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: Web Browser SSO Profile では <Response> の配送に HTTP-Redirect を使えない（IIP-SSO01.x）。Artifact 非対応の対象では応答 binding の合法な値が HTTP-POST しかなく、属性を読んでいることの積極的証明は誤り応答の観測に頼るしかない。『検証できなかった』を『適合』と書かないために、not_verified の条件を明記している。
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP12.b</code> の詳細</summary>

- **必要な variant**:
  - `v-0716caa555` メタデータに存在しない ACS URL を指定 → その URL には返さない（エラー <Status> を返すか既定 ACS に返す）
  - `v-970a87f472` 他エンティティのメタデータにある ACS URL を指定 → そこには返さない
  - `v-415de228a1` メタデータに存在しない index を指定 → その扱いは IIP-IDP12.d、ただし要求元に紐づかない場所へは返さない
  - `v-a3dfa1fe3e` 対照: メタデータに存在する ACS URL を指定 → そこに返る（すべて拒否する実装を落とす）
- **対照（negative control）**:
  - ★ この義務が SAML の代表的な脆弱性（オープンリダイレクタ化）に対応する。『メタデータにある URL は通る』ケースを対にしないと、全拒否実装を PASS にしてしまう
  - ★ 署名済み AuthnRequest でも同じ。SAML2Prof 4.1.4.1 は『Whether the request is signed or not, the identity provider MUST ensure ...』と述べる。署名の有無で 2 ケースにする
  - ★ 『返さない』の観測は、ブラウザの遷移先 URL と <Response> の宛先の両方で確認する。エラー画面を出すか既定 ACS に返すかは実装依存で、どちらでも違反ではない
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: IIP-IDP05（エラー時の応答先が acceptable location であること）と対になる。SAML2Prof 4.1.3.5 の『The identity provider MUST have some means to establish that this location is in fact controlled by the service provider』も同旨だが、IIP-IDP12 の参照先は [SAML2Core] なので根拠は 3.4.1 に置く。
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP12.c</code> の詳細</summary>

- **必要な variant**:
  - `v-be44343de9` 3 属性すべて省略 → md:AssertionConsumerService/@isDefault="true" のエンドポイントに返る
  - `v-51977ecb6d` isDefault の明示がないメタデータ → SAML2Meta の既定規則（最小 index）で選ばれたエンドポイントに返る
  - `v-5c27acabef` 対照: 既定でない ACS を isDefault に変えて再取得 → 返送先が変わる（メタデータを読んでいることの証明）
- **対照（negative control）**:
  - ★ ACS が 1 つしかないメタデータでは検出力がない。2 つ以上を持つ variant で試す
  - ★ 『既定』の決定は SAML2Meta の規則による。IIP-MD05 / IIP-SSO06 のメタデータ消費と重なるが、ここで判定するのは『既定ロケーションに返すこと』だけ
- **参照先仕様**: `SAML2Core#3.4.1`
- **source_clauses**: `[0, 247)` `sha256:3662ba485eda…`
- **review**: `PENDING_REVIEW` / reviewer: `None` / approved_at: `None`

</details>

<details><summary><code>IIP-IDP12.d</code> の詳細</summary>

- **必要な variant**:
  - `v-8f9d5055b6` メタデータに存在しない index を指定 → エラー <Status> を持つ <Response> が返る（許容）
  - `v-29b7916187` メタデータに存在しない index を指定 → 既定 ACS に返る（許容）
- **対照（negative control）**:
  - ★ MAY 義務。どちらの挙動でも適合であり、判定は『この 2 つ以外の挙動をしていないこと』に限る。特に、要求元に紐づかない場所へ返したら IIP-IDP12.b の違反になる
  - ★ 片方だけを期待するケースを書くと、もう片方を選んだ適合実装を FAIL にする
- **参照先仕様**: `SAML2Core#3.4.1`
- **注記**: MAY_CLASS なので Evaluator は PASS / NOT_SUPPORTED しか出さない。『どちらでもない第 3 の挙動』の検出は IIP-IDP12.b が担う。
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
obligations    : 343
未承認         : 343
未解決 open Q  : 11 ['IIP-MD05.a', 'IIP-MD05.b', 'IIP-MD05.c', 'IIP-MD05.d', 'IIP-MD05.e', 'IIP-MD05.f', 'IIP-MD06.a', 'IIP-SP14.a', 'IIP-IDP13.a', 'IIP-IDP17.a', 'IIP-IDP17.b']
```

作成者は `reviewer` / `approved_at` を埋めていません。
別のレビュアーが**原文と `tests/coverage.yaml` を直接照合**して承認するまで、テスト実装に着手しません。
