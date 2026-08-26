# 11. 設計レビュー記録

## R1 — 2026-08-25 判定モデル・カバレッジ定義のレビュー

**結論**: 指摘は 9 件すべて妥当だった。うち 3 件（指摘 4 の RFC2119 レベル読み違い）は
[Kantara IIP v1.1 原文](https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html)
を再取得して照合し、確認したうえで修正した。

### 反映結果

| # | 指摘 | 判定 | 対応 |
|---|---|---|---|
| 1 | 実行できない MUST を `NOT_APPLICABLE` にすると検証を回避できる | **妥当** | `NOT_VERIFIED`（reason 必須）を新設。`NOT_APPLICABLE` は「役割違い」「条件付き義務の条件が偽」の 2 つのみに限定。CI に `NotApplicableGuardTest` を追加 → [03 §4](03-test-model.md), [05 §5](05-test-definition-format.md) |
| 2 | 集約規則が未実施ケースを PASS にする / `INCONSISTENT` が語彙にない / FAIL が ERROR に隠れる | **妥当** | 重大度順序を一意に定義（`FAIL > INCONSISTENT > ERROR > INDETERMINATE > NOT_VERIFIED > WARNING > PASS > …`）。`INCONSISTENT` を語彙に追加。決定表を作成し、10×10 の全組み合わせをテーブル駆動テストで固定 → [03 §6](03-test-model.md) |
| 3 | YAML が役割別・条項別の RFC2119 レベルを表現できない | **妥当** | **Obligation 層**を新設。`coverage.yaml` に `obligations[]`（roles / level / condition）を持ち、テスト定義は `obligation:` を指す。判定レベルはカタログのみが持ち、テスト定義にも実装にも書かない → [03 §1](03-test-model.md), [05 §2](05-test-definition-format.md) |
| 4 | 一部のテスト方針が原文より厳しく偽 FAIL を出す | **妥当（原文で確認）** | 下表の通り訂正 → [04](04-requirement-coverage.md) |
| 5 | 「v0.1 で全 69 要件」と実際の計画が矛盾 / 集計が表と不一致 | **妥当** | 「掲載する」と「判定可能にする」を分離。IIP-SP05 を `secondary_peer` オプションで Phase 1 に戻した。手書き集計を削除し `coverage.yaml` からの生成に切替 → [01](01-scope-and-roadmap.md), [04](04-requirement-coverage.md) |
| 6 | Transcript が ECP のパスワードを保存する設計になっている | **妥当** | Recorder の入口に **Redactor** を置き、`Authorization` / `Cookie` / パスワード相当のフォーム値を**永続化前に不可逆除去**。`RedactorTest` で `/data` 全体を検査 → [02 §5.2](02-architecture.md), [08 §4](08-suite-security.md) |
| 7 | 対話ステップを同期的な `execute()` で再開できない | **妥当** | `start(ctx)` / `resume(ctx, state, event)` の明示的状態遷移に変更。`CaseStep` は sealed interface、`CaseState` は JSON 化して SQLite に永続化。冪等性を規約化 → [05 §4](05-test-definition-format.md) |
| 8 | ECP エンドポイントの役割が逆 | **妥当** | IdP の ECP 対応を試験するとき Samlier は **ECP クライアント + SP**。`/p/{plan}/idp/ecp` を削除し `/p/{plan}/sp/paos`（PAOS Response Consumer）を追加。メタデータに PAOS ACS を含める → [02 §3.7](02-architecture.md) |
| 9 | Preflight だけでは Target→Suite 到達性を判定できない | **妥当** | 到達性を `ASSERTED` / `CONFIRMED` に分離。nonce を仕込んだメタデータへの inbound を観測して初めて `CONFIRMED` に昇格。`requires.reachability` を宣言したケースはそれまで実行しない → [07 §2](07-deployment-and-networking.md) |

### 指摘 4 の原文照合結果

| 要件 | 原文（該当部） | 修正前の誤り | 訂正後 |
|---|---|---|---|
| **IIP-MD09** | *Implementations **MUST be capable of publishing** the cryptographic capabilities … It is **RECOMMENDED** that they support dynamic generation* | 「メタデータに `alg:*` がなければ FAIL」 | MUST は「公開**できる**こと」。メタデータに宣言がないだけでは FAIL にせず、公開機能の有無を `ATTESTED` で確認。動的生成は RECOMMENDED → 未対応は WARNING |
| **IIP-ALG08** | *MUST support the ability to prevent the use of particular algorithms … The set … **MUST be configurable** and it is **RECOMMENDED** that the default set include …* | 「既定で MD5 等が無効でなければ FAIL」 | 既定集合は RECOMMENDED → WARNING。MUST は「禁止できること」「集合が設定可能なこと」。利用者に禁止設定をしてもらってから検証する `CONFIG` に変更 |
| **IIP-SP13** | *Service Providers **MUST** support the ability to reject unsigned `<samlp:Response>` elements and **SHOULD** do so by default* | 「既定設定で拒否しなければ FAIL」 | 既定拒否は SHOULD → WARNING。FAIL は「設定しても拒否できない」場合のみ |

**併せて訂正した要件**

| 要件 | 内容 |
|---|---|
| IIP-MD01 / IIP-MD10 | *Identity Providers **MUST** and Service Providers **SHOULD*** — 役割別に義務を分割 |
| IIP-SP14 | *SPs **SHOULD** support … SPs **that claim support** … **MUST** be capable of issuing* — 条件付き MUST として `condition` で表現。表明しなければ `NOT_APPLICABLE` <br>⚠ **R2 で訂正**: 「唯一の条件付き例」と書いたのは誤り。IIP-SP15 / SP16 / SP17 も同じ SLO 条件付き、IIP-MD08 は outbound 暗号化条件付きだった |
| IIP-G02 | *MUST be able to **accept**, without error or truncation …* — 受信側の義務なので**両役割で試験できる**。`N`（検証不能）としていたのは誤り。IdP には `AuthnRequest/@ProviderName` に 256 文字を入れて送る |
| IIP-IDP21 | *in a manner that **allows deployers to avoid** assignment of identifiers that differ only by case* — 生成方式の**設定可能性**に関する義務。観測した 1 件の NameID の文字集合からは判定できず、`[A-Za-z]` 混在を理由に WARNING を出すのは誤り（UUID や Base64 でも要件は満たしうる） |
| IIP-SP04 | MUST の未実装申告は `NOT_SUPPORTED` ではなく **FAIL(declared-unsupported)**（`NOT_SUPPORTED` は MAY/OPTIONAL 専用） |

### 未反映・持ち越し

| 項目 | 状態 |
|---|---|
| 全 69 要件 × 義務単位の RFC2119 レベル再照合 | **未完了**。今回照合したのは 9 件のみ。`coverage.yaml` の作成時に全件を原文と 1 行ずつ突き合わせる。3/9 に読み違いがあったことから、**残りにも同程度の誤りがある前提で進める** |
| 手書きカバレッジ表の生成物化 | 実装開始時（[09 D-10](09-open-decisions.md)）。それまで [04](04-requirement-coverage.md) の集計値は使わない |
| Core / Full の義務単位への割り当て | `coverage.yaml` 作成時 |

### この修正で変わった前提

- **クイック実行モードの結果は「適合」を名乗れない**。飛ばした義務は `NOT_VERIFIED` として
  母数に残り、Run 判定は `INCOMPLETE` になる。UI で明示する
- **`CONFORMANT` を単独で表示することを禁止**。常に「外部から検証可能な MUST 義務 N 件中 M 件が通過。
  検証不能な MUST 義務 K 件は評価していない」という定型文を伴う（[03 §7.3](03-test-model.md)）
- **判定レベルの唯一の出典は `coverage.yaml`**。テスト定義も実装も FAIL/WARNING を決めない。
  ケースは `outcome`（satisfied / violated / …）だけを返し、Runner がレベルと突き合わせて Verdict にする

---

## R2 — 2026-08-25 結果 JSON・条件付き義務・ECP のレビュー

**結論**: 指摘 14 件すべて妥当だった。仕様解釈に関わる 6 件（2, 4, 5, 6, 7, 8）は
原文を再取得して照合し、**すべて指摘の通りであることを確認**した。
R1 で「9 件すべて反映済み」と述べたが、**反映は不完全だった**（指摘 12 の残存箇所）。

### 反映結果

| # | 指摘 | 判定 | 対応 |
|---|---|---|---|
| 1 | `result.json` 例が自身の判定規則と矛盾（未解決 MUST があるのに CONFORMANT 系、IdP Run に SP 専用義務、`not_observable` キー重複） | **妥当** | 手書きの例を**全廃**。構造のみを残し、値は `Evaluator` の golden fixture に。JSON Schema + 不変条件テスト 10 件を定義 → [06 §1](06-results-and-publication.md), [03 §7.5](03-test-model.md) |
| 2 | SP15〜17 も条件付き MUST。「SP14 が唯一」は誤り | **妥当（原文で確認）** | 3 件を条件付き MUST に訂正。R1 の記述も訂正 → [04](04-requirement-coverage.md) |
| 3 | 条件判定を自己申告だけにすると義務を回避できる | **妥当** | 条件を **三値評価**（TRUE / FALSE / UNKNOWN）に。`observed` 材料を必須化し、申告と観測の矛盾は `INCONSISTENT`（観測優先）。UNKNOWN は `NOT_VERIFIED(applicability_undetermined)` → [03 §1](03-test-model.md), [05 §2.1](05-test-definition-format.md) |
| 4 | IIP-MD08 の条件とテスト対象が誤り（SP08 と取り違え） | **妥当（原文で確認）** | outbound 暗号化への条件付き MUST に訂正。「ピアの複数暗号鍵を消費できるか」に対象を修正 → [04](04-requirement-coverage.md) |
| 5 | IIP-MD04 の too distant 閾値は**対象側で設定可能**。Samlier が 90 日で FAIL にするのは誤り | **妥当（原文で確認）** | 独自閾値を撤回。対象側で閾値 T を設定してもらい、`T−δ` / `T+δ` の**境界値ペア**で検証。設定可能性自体も義務 → [09 D-14](09-open-decisions.md), [04](04-requirement-coverage.md) |
| 6 | IIP-IDP15 の検査対象は `samlec:GeneratedKey`（SAML-EC ドラフト §5.3.1） | **妥当（原文で確認）** | `ecp:RelayState`/`ecp:Request` を検査するという記述は誤り。`peer/ecp/` を `profile/` と `samlec/` に分割。参照ドラフトの版を `specs.yaml` に固定 → [02 §3.7](02-architecture.md) |
| 7 | IIP-IDP13 は channel bindings の検証も MUST | **妥当（原文で確認）** | *MUST support "Bearer" subject confirmation **and verification of channel bindings***。5 ケースを定義 → [02 §3.7](02-architecture.md), [04](04-requirement-coverage.md) |
| 8 | MD02 / ALG06 / SP09 / IDP05 / IDP17 のカバレッジ不足。全件照合を設計ゲートにすべき | **妥当（原文で確認）** | 5 件すべて訂正。**設計ゲート G1** を新設し、テスト実装の前段に置いた → [04 設計ゲート G1](04-requirement-coverage.md), [01](01-scope-and-roadmap.md) |
| 9 | 中断・再開 API にクラッシュ整合性がない | **妥当** | ケース実装は送信せず、`OutboundAction` を返す **outbox 方式**に。次状態と送信意図を同一トランザクションで永続化。`actionId` は state から決定論的に導出（`UUID.randomUUID()` を静的解析で禁止） → [05 §4.3](05-test-definition-format.md) |
| 10 | 判定の正本が digest に含まれていない | **妥当** | `evaluation_bundle.digest` を新設（`coverage.yaml` + `defs/*` + `specs.yaml` + outcome 写像版 + 集約ポリシー版）。外部ドラフトは版まで固定 → [06 §1](06-results-and-publication.md) |
| 11 | シークレット URL の扱いが未設計 | **妥当** | クエリを廃し **fragment → HttpOnly/Secure/SameSite Cookie 交換**。トークンはハッシュ保存、公開 ID と分離、`Referrer-Policy: no-referrer`、CSRF 対策、ローテーション・失効 → [09 D-09](09-open-decisions.md) |
| 12 | 旧記述が残り R1 の指摘 1・2 が完全反映されていない | **妥当** | 6 箇所すべて修正（README, 03 ×3, 07, 09 D-10） |
| 13 | Core/Full 定義がカバレッジ表と不一致 | **妥当** | `Full = 全義務` / `Core ⊂ Full` と定義し直し、選定基準を明文化。IIP-MD02 を Core に訂正。`level_assignment` を義務単位に → [01](01-scope-and-roadmap.md) |
| 14 | Run 判定とカバレッジ率の定義が不整合。全レベルの写像が未定義 | **妥当** | `satisfied ≡ {PASS, WARNING}` を導入し 4 判定を網羅的・排他的に。`verified_ratio = must_resolved / must_observable` と分母を一意化。8 レベルを 3 クラスに正規化する表を追加 → [03 §7.2/§7.4](03-test-model.md), [05 §2.3](05-test-definition-format.md) |

### 原文照合の結果（R2）

| 要件 | 原文の該当部 | 修正前の誤り |
|---|---|---|
| IIP-SP15/16/17 | いずれも *SPs that support the SingleLogout profile …* | 無条件 MUST として扱っていた |
| IIP-MD08 | *implementations that support outbound encryption* … *consume any number of encryption keys bound to a single role descriptor* | 無条件 MUST。かつ「SP の復号鍵ロールオーバー」と取り違えていた（それは IIP-SP08） |
| IIP-MD04 | *too far into the future (**configurable**)* | Samlier が 90 日という絶対閾値で FAIL 判定 |
| IIP-MD02 | *redirects (301, 302, 307) MUST be honored* / *both `<md:EntityDescriptor>` and `<md:EntitiesDescriptor>`* / *any number of child elements* | 3 条項が欠落。逆に**原文にない ETag / Last-Modified** を検査対象にしていた |
| IIP-ALG06 | `rsa-oaep-mgf1p` / `rsa-oaep` / DigestMethod **sha256 と sha1 の両方** / **既定 MGF1-SHA1** | 後半 3 条項が欠落 |
| IIP-SP09 | *preserve POST bodies across successful SSO*（RECOMMENDED、サイズ制限あり） | 欠落 |
| IIP-IDP05 | *provided that the user agent remains available **and an acceptable location … is known*** | 未登録 ACS を FAIL 条件に使っていた（原文はその場合エラー Response を返さないことを許す） |
| IIP-IDP13 | *MUST support "Bearer" subject confirmation **and verification of channel bindings*** | channel bindings が欠落 |
| IIP-IDP15 | *in accordance with **[SAML-EC], Section 5.3.1*** | ECP Profile の要素を検査するとしていた |
| IIP-IDP17 | *MUST support … SingleLogout profile **and** the … Asynchronous Single Logout Protocol Extension* | Async SLO 固有のケースが未定義 |

### この修正で変わった前提

- **テストケースの実装前に設計ゲート G1（全 69 要件の原文照合）を置く**。
  17 件照合して 11 件に誤りがあった以上、残り 52 件も同様と考えるべき
- **ドキュメント中の JSON 例は手書きしない**。`Evaluator` の出力を golden fixture として生成する
- **自己申告だけで MUST 義務を除外できない**。条件には観測材料が必須
- **Samlier が仕様にない絶対閾値を判定に使わない**（IIP-MD04.c）。
  設定可能な閾値は対象側に設定させ、境界値で検証する
- **ケース実装は自分で送信しない**。outbox 経由でクラッシュ整合性を確保する

---

## R3 — 2026-08-25 適用除外・ECP 詳細・配信保証のレビュー

**結論**: 指摘 11 件すべて妥当だった。仕様に関わる 3 件（1, 2, 3）は原文で確認し、
すべて指摘の通りであることを確認した。加えて照合中に **IIP-IDP13 の
`excepting IIP-SSO02 and IIP-SSO03`** という未記載の除外も見つかった。

### 反映結果

| # | P | 指摘 | 判定 | 対応 |
|---|---|---|---|---|
| 1 | P1 | IIP-IDP13 の token translation Proxy 適用除外が欠落 | **妥当（原文で確認）** | *This requirement does not apply to token translation Proxies.* を確認。Test Plan に `target.kind` を追加し条件付き義務化。**加えて `excepting IIP-SSO02 and IIP-SSO03` も未記載だったので追記** → [04](04-requirement-coverage.md), [03 §2](03-test-model.md) |
| 2 | P1 | ECP→IdP に PAOS ヘッダを残している | **妥当（原文で確認）** | ECP v2 §2.3.4 *Any header blocks received from the service provider **MUST be removed***。区間ごとのヘッダ集合を表にし、`EcpClient` が SP 由来ヘッダを保持しないデータ構造にすることを規定 → [02 §3.7](02-architecture.md) |
| 3 | P1 | channel binding の成功ケースが出力を検証していない | **妥当（原文で確認）** | §2.3.6.2 は一致時に `cb:ChannelBindings` を **SOAP ヘッダと `<saml:Advice>` の両方**に含めることを MUST としている。片方だけなら違反。ケース 5 も *MUST be signed if the channel bindings extension option is used* に基づき「未署名ならエラー Response」と期待を具体化 → [02 §3.7](02-architecture.md) |
| 4 | P1 | outbox で exactly-once は保証できない | **妥当。前版の記述は論理が逆だった** | 「同一 ID なら リプレイ検出に引っかからない」は誤りで、**同一 ID の再送こそ検出対象**。`UNKNOWN_DELIVERY` 状態を新設し、①まず inbound を待つ ②`replay_safe` なら再送 ③それ以外は `NOT_VERIFIED(delivery_unknown)` ④**再送時の replay エラーを対象の FAIL にしない**、を規定。`suite_incidents[]` に別枠記録 → [05 §4.3.1](05-test-definition-format.md), [06 §1](06-results-and-publication.md) |
| 5 | P1 | 永続 outbox と ECP 資格情報の非保存が両立していない | **妥当** | 資格情報を `payload` に載せず実行時注入に変更。`requiresEphemeralCredential` を追加し、再起動後は `WAITING_CREDENTIAL` で再入力待ち。拒否・TTL 超過は `NOT_VERIFIED(credential_unavailable)`。暗号化秘密ストア案は「鍵も `/data` にある」ため不採用 → [05 §4.3.2](05-test-definition-format.md), [03 §8](03-test-model.md) |
| 6 | P2 | 適用性の矛盾が Verdict に接続されていない | **妥当** | `ApplicabilityEvaluation` を `Evaluator.evaluate()` の明示的入力に。`CONFLICT` は `INCONSISTENT` として集約に入り、重大度順序上 `PASS` より上位なので**矛盾したまま PASS にならない**。`applicability[]` に `declared` / `observed` / `conflict` を追加 → [03 §6.2](03-test-model.md), [06 §1](06-results-and-publication.md) |
| 7 | P2 | Full 実行でも未評価の SHOULD が無視される | **妥当** | **適合性と実行完全性を別フィールドに分離**。`run.conformance`（MUST のみ）と `run.completeness`（選択プロファイルの全義務）。両方の併記を必須化。旧 `INCOMPLETE` は適合性側では `INDETERMINATE` に改称 → [03 §7.2](03-test-model.md) |
| 8 | P2 | MD04 の設定不能時の判定が矛盾 | **妥当** | (a) 製品に設定機能が存在しない → **FAIL** / (b) 機能はあるが利用者が確認・変更できない → **`NOT_VERIFIED(target_config_unavailable)`** に分離。質問文で明示的に選ばせる → [09 D-14](09-open-decisions.md) |
| 9 | P2 | G1 が「レビュー可能」までで承認を要求していない | **妥当** | 全義務に `review: { reviewer, approved_at, source_digest, spec_version }` を必須化。**reviewer が作成者と同一なら CI が落ちる**。参照版変更・`source_digest` 不一致・要約/レベル/条件の編集で**承認が失効**する規則を追加 → [04 設計ゲート G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |
| 10 | P2 | fragment トークンがブラウザ履歴に残る | **妥当** | 値の読み取り**直後・ネットワーク処理より前**に `history.replaceState` で除去（交換の成否によらず実行）。管理画面に厳格な CSP、`POST /api/manage/session` の `Origin` 検証を追加 → [09 D-09](09-open-decisions.md) |
| 11 | P3 | `conformance_statement` の例が役割混在 | **妥当** | SP の Run に `IIP-IDP02.b` が混在し `IIP-SP11.b` が存在しないキーだった。**例を削除**し、golden fixture からの生成に切り替えるまで置かない → [03 §7.3](03-test-model.md) |

### この修正で変わった前提

- **適用除外の文を要件末尾に持つ要件がある**。G1 の分解手順に「除外の文を見落とさない」を明記した。
  IIP-IDP13 だけで 2 つの除外（token translation Proxy / IIP-SSO02・SSO03）があった
- **exactly-once は目標にしない**。目標は「Suite の不確実性を対象の不適合に転嫁しない」こと。
  Suite が自分の障害で他人の製品を FAIL と表示するのが最悪の失敗である
- **適合性と実行完全性は 2 つのフィールド**。1 つに畳むと必ずどちらかが隠れる
- **G1 は作成者だけでは通過できない**。義務ごとに作成者以外の承認と原文ダイジェストを要求し、
  参照版が変われば承認が失効する
- **ドキュメント中の例は JSON も文章も全て生成物**にする。手書きの例は 3 回連続で不整合を生んだ

### 未反映・持ち越し

| 項目 | 状態 |
|---|---|
| 全 69 要件の原文照合と独立レビュー承認 | **設計ゲート G1**。実装着手の前提条件 |
| `Evaluator` / golden fixture の実装 | M0。それまでドキュメント中の例は「構造のみ」 |
| ECP v2 §2.3 の全ヘッダ仕様の精査 | G1 に含める（今回確認したのは §2.3.4 / §2.3.6.2 のみ） |

---

## R4 — 2026-08-25 原文追加照合・適用性の方向性・配信保証のレビュー

**結論**: 指摘 11 件すべて妥当だった。追加照合した 3 件（SSO07 / ALG05 / SP04）は
**3 件とも原文と意味がずれていた**。累計の誤読率は **14/20**。
G1 を実装前ゲートにした判断は妥当だったと確認できた。

### 原文照合の結果（R4）

| 要件 | 原文 | 修正前の誤り |
|---|---|---|
| **IIP-SSO07** | *REQUIRED that implementations **successfully process** messages containing any optional content* — 処理は **SAML2Core の要素別処理規則**に従い、要素によっては**エラーが正しい** | 「未対応でも**処理を継続**する」としていた。**正しくエラーを返す実装を不適合にする**期待値だった |
| **IIP-ALG05** | `.a` MAY（CBC 対応）+ `.b` *Implementations supporting them **SHOULD warn on use*** | `.b` が欠落。逆に**原文にない**「CBC が既定なら WARNING」という独自条件を持っていた |
| **IIP-SP04** | `.a` MUST（IdP Discovery 対応）+ `.b` *discovery mechanisms **SHOULD use SAML metadata** to determine the endpoint(s)* | `.b` が欠落 |

IIP-SSO07 は **IIP の文だけでは期待値が決まらず、SAML2Core まで遡る必要がある**類型だった。
G1 の分解手順に「参照先仕様まで遡る」を追加した。

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | IIP-SSO07 は「処理継続」ではない | 要素別に期待値を分けたケースへ。満たせない `<Subject>` → **エラーが正**、未知の `<Conditions>` 子要素 → 無視可。各要素の期待値は G1 で SAML2Core まで遡って確定 → [04](04-requirement-coverage.md) |
| 2 | P1 | IIP-ALG05 の条件付き SHOULD が欠落 | `.a` MAY / `.b` 条件付き SHOULD に分解。独自条件「CBC が既定なら WARNING」を削除 → [04](04-requirement-coverage.md) |
| 3 | P1 | IIP-SP04 の SHOULD が欠落 | `.a` MUST / `.b` SHOULD に分解。固定 URL 手入力のみの実装は WARNING → [04](04-requirement-coverage.md) |
| 4 | P1 | 自己申告だけで条件付き MUST を除外できる | 述語に **`CLAIM_BASED` / `CAPABILITY_BASED` / `CLASSIFICATION_BASED`** の種別を導入。後 2 者の declaration-only FALSE は `UNKNOWN`。`CLASSIFICATION_BASED` のみ、明示的な除外申告があれば FALSE を採るが `excluded_by_declaration` に計上し `conformance_statement` に必ず明記 → [03 §1](03-test-model.md) |
| 5 | P1 | `CONFLICT` が適用性の方向を失う | **`effective_result`（TRUE/FALSE/UNKNOWN）と `conflict`（bool）を分離**。前者でケースのスケジューリング、後者で `INCONSISTENT` を注入。`declared=false/observed=true` と `declared=true/observed=false` が区別できるようになった → [03 §1, §6.2](03-test-model.md) |
| 6 | P1 | `UNKNOWN_DELIVERY` の不変条件が正当な FAIL を禁止 | 不変条件 9c のスコープを **当該 CaseRun のみ**に限定。同じ義務の別ケースが違反を証明していれば義務は `FAIL` が正しい → [06 §1.2](06-results-and-publication.md) |
| 7 | P1 | Evaluator の署名から `ApplicabilityEvaluation` が消えている | §7.5 の署名を正本とし、`applicability` と `incidents` を追加。§6.2 は参照のみに → [03 §7.5](03-test-model.md) |
| 8 | P2 | `CONFORMANT_WITH_WARNINGS` の WARNING 範囲が未定義 | `W = applicable ∩ selected_profile` と明示。合否は `must_observable`、WARNING の計数は選択プロファイル全体。SHOULD 違反が隠れない → [03 §7.2](03-test-model.md) |
| 9 | P2 | 二軸判定への移行が文書全体で未完了 | README / 01 / 03 / 04 / 07 の旧単一ラベルを全て二軸表記に移行。公開ページのワイヤーフレームにも `Conformance` / `Completeness` を併記 → 全文書 |
| 10 | P2 | `replay_safe` の自己宣言は検証不能 | ケースからの宣言を**廃止**。再送可否を `OutboundKind` 単位で Runner が固定する allowlist に（`Retry.SAFE` は GET かつ状態を持たないものだけ）。迷ったら `UNSAFE` → [05 §4.3.1](05-test-definition-format.md) |
| 11 | P2 | オリジン分離が「検討」のまま / CSP に nonce がない | 配備モード別の規範レベルに統一（Hosted は **MUST**、同一オリジンなら起動拒否）。CSP に `'nonce-{per-response-random}'` を明示し `'unsafe-inline'`/`'strict-dynamic'` を禁止 → [08 §5](08-suite-security.md), [07 §7](07-deployment-and-networking.md), [09 D-09](09-open-decisions.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| **累計** | **20** | **14** |

残り 49 要件も同程度の誤りを含む前提で G1 に臨む。
特に **IIP の文だけで期待値が決まらない義務**（SAML2Core / ECP Profile / SAML-EC /
Async SLO / IdPDisco / MetaIOP へ遡るもの）を分解時に洗い出す。

### この修正で変わった前提

- **適用性は 2 つの値**。「実行すべきか」と「矛盾があるか」を 1 つに畳まない
- **申告の重みは条件の性質で変わる**。「対応を表明しているか」が条件そのものの義務だけ、
  申告を真理値として採用してよい
- **再送可否をケース作者に決めさせない**。CI で証明できない安全性を宣言させない
- **Suite の障害は当該ケースだけを汚染する**。別ケースが証明した FAIL を隠さない
- **オリジン分離は Hosted で MUST**。起動時に強制する

---

## R5 — 2026-08-25 原文追加照合・除外の機械可読性のレビュー

**結論**: 指摘 10 件すべて妥当だった。追加照合した 5 件（G03 / MD01 / MD03 / MD12 / SP06）は
**5 件とも未分解の規範内容があった**。SSO07 は R4 の修正が部分的だったことも確認した。
累計の誤読率は **19/25**。

### 原文照合の結果（R5）

| 要件 | 原文 | 修正前の欠落 |
|---|---|---|
| **IIP-G03** | `.a` *MUST not send … SAML protocol messages containing a DTD* / `.b` *MUST have the ability to **reject***  | `.a`（送信側の MUST NOT）が欠落。受信拒否だけを見ていた |
| **IIP-MD01** | + *Implementations **that claim support** for this protocol MUST be able to request and utilize metadata from one or more MDQ responders* | `.b`（`CLAIM_BASED` 条件付き MUST）が欠落 |
| **IIP-MD03** | + *MUST be possible to **ignore the other contents of the certificate** and verify … based solely on the public key* / + *MUST be possible to **limit the use of a trusted key to a single metadata source*** | `.b` `.c` が欠落 |
| **IIP-MD12** | *any number of long-lived, self-signed …* / *expired …* / *any digest algorithm …* / 証明書は *not yet valid, carry critical or non-critical extensions* でもよい | バリエーション不足（複数証明書・not-yet-valid・拡張・KeyUsage） |
| **IIP-SP06** | + *MUST be capable of including **any number of** `AuthnContextClassRef` elements* | `.b` が欠落。単一 ClassRef だけで PASS になりえた |
| **IIP-SSO07** | *such content MUST either result in errors or be ignored, **as directed by the processing rules for the element or attribute in [SAML2Core]***。例示は `<saml:Subject>` / `<saml:Conditions>` / `<samlp:AuthnRequest>` | R4 の修正が部分的。「エラーも無視も可」では**検出力がゼロ** |

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | 公開ページ例が二軸判定と矛盾（`Resolved 45/47` + `NOT_VERIFIED 2` なのに `CONFORMANT`） | **数値例を削除**。公開ページも golden fixture から生成する。「必ず含める項目」の規定だけを残した → [06 §3](06-results-and-publication.md) |
| 2 | P1 | IIP-SSO07 の期待値が未確定 | **ケース化の規則**を定義: SAML2Core が**一意の結果を規定している要素だけ**を verdict 対象にし、両方許される要素は情報記録のみ。前版の「未知の `<Conditions>` 子要素」は削除（それは IIP-EXT01 の領域）。`<saml:Subject>` は verdict 対象になりうる。各要素の確定は G1 → [04](04-requirement-coverage.md) |
| 3 | P1 | 新述語モデルを coverage.yaml / CI 規則が表現できない | `predicate_kind` をスキーマに追加。`observed` の必須性を種別ごとに分岐（CI 規則 5b-1〜5b-3）。`CLAIM_BASED` は原文に *claim(s) support* 相当の語があることを `source_digest` の対象文で CI 確認する → [05 §2.1, §5](05-test-definition-format.md) |
| 4 | P1 | 自己申告による除外でも `CONFORMANT` を返せる | **enum の値そのもの**に現れるようにした: `CONFORMANT_WITH_DECLARED_EXCLUSIONS` を新設。`run.conformance == "CONFORMANT"` で分岐する素朴な利用者は一致しない。`run.scope_qualifications[]`（理由・申告者・時刻・除外義務一覧）と `target.kind` を結果に追加 → [03 §1, §7.2](03-test-model.md), [06 §1](06-results-and-publication.md) |
| 5 | P1 | IIP-G03 の送信側 MUST NOT が欠落 | `.a` を追加。**対象が生成した全 SAML プロトコルメッセージに `<!DOCTYPE` がないこと**を Transcript 全件に対する受動チェックとして横断適用 → [04](04-requirement-coverage.md) |
| 6 | P1 | IIP-MD01 の条件付き MUST が欠落 | `.b` を `CLAIM_BASED` 義務として追加。`secondary_peer` の未登録 entityID でメッセージを送り、MDQ で動的取得できるかを検証 → [04](04-requirement-coverage.md) |
| 7 | P1 | IIP-MD03 の鍵処理義務が 2 つ欠落 | `.b`（証明書の他の内容を無視し公開鍵のみで検証）/ `.c`（信頼鍵を単一メタデータソースに限定）を追加 → [04](04-requirement-coverage.md) |
| 8 | P1 | IIP-MD12 の証明書バリエーション不足 | 複数証明書 / not-yet-valid / critical extension / 制限的 KeyUsage / SHA-512 を variant に追加 → [04](04-requirement-coverage.md) |
| 9 | P1 | IIP-SP06 の「任意個数の ClassRef」が欠落 | `.b` を追加。0 / 1 / 複数の ClassRef を設定してもらい生成物を検査 → [04](04-requirement-coverage.md) |
| 10 | P2 | 廃止した `CONFLICT` が不変条件に残存 | 不変条件 9 を `effective_result` + `conflict` の構造に更新。`CONFLICT` という値が存在しないことを明記 → [06 §1.2](06-results-and-publication.md), [05 §5](05-test-definition-format.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| **累計** | **26** | **20** |

**照合した要件の 8 割近くに誤りがあった。** 残り 43 要件も同じ前提で扱う。
特に **「+ もう 1 つの MUST」が文の後半に隠れている**類型（MD01 / MD03 / SP06 / G03）が多い。
G1 の分解では、**要件を 1 文ずつでなく 1 節ずつ読み切る**こと。

### この修正で変わった前提

- **除外は enum の値に現れる**。第 2 のフィールドを読み飛ばされる設計にしない
- **検出力のないケースは作らない**。「エラーでも無視でもよい」は義務を検証していない
- **述語の種類ごとに CI 規則を分岐**する。一律の必須化は新モデルと矛盾する
- **手書きの例は全廃**。JSON・適合表明・公開ページの 3 か所すべてを生成物にする
  （手書きは 4 回連続で不整合を生んだ）

---

## R6 — 2026-08-25 除外範囲・仕様分解の整合性レビュー

**結論**: 指摘 10 件すべて妥当だった。追加照合した 4 件（MD05 / MD06 / SSO06 / IDP04）は
**4 件とも未分解が残っていた**。累計の誤読率は **25/31**（集計表と一致）。

### 原文照合の結果（R6）

| 要件 | 原文 | 修正前の欠落・誤り |
|---|---|---|
| **IIP-IDP13 の除外範囲** | *This requirement does not apply to token translation Proxies.* は **IIP-IDP13 の末尾の文** | 除外例に IIP-IDP14 以降を含めていた。IDP14〜16 は**無条件 MUST** |
| **IIP-MD05** | 必須は **6 仕様**（SAML V2.0 Metadata / Schema / Metadata IOP / Entity Attributes / Algorithm Support / Login and Discovery UI）+ *other metadata extension content … **MUST NOT** prevent consumption and use* | 6 仕様を個別義務にしていない。MUST NOT が欠落。**`mdrpi` を必須に含めていたが原文のリストにない** |
| **IIP-MD06** | *interoperating with **any number of** SAML peers … **without additional inputs or separate configuration*** / 信頼はメタデータのみから導出でき、署名検証にも SOAP/TLS にも別 trust store を要求しない | 証明書の PKIX 処理（実際は MD12 / MD03.c の領域）しか扱っていなかった |
| **IIP-SSO06** | *for any metadata element identified as "MUST" or "MAY" in the Web Browser SSO Profile **Use of Metadata** section*（[SAML2Prof] **§4.1.6**） | 「利用者の申告」だけで、要素の列挙も追従の検証もなかった |
| **IIP-IDP04** | `.a` *RequestedAttribute … **including the isRequired XML attribute*** / `.b` *support the **AttributeConsumingServiceIndex** attribute*（別の MUST） | `isRequired` が未明示。`.a` と `.b` を分けていなかった |

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | token translation Proxy の除外範囲が広がっている | 除外を **IIP-IDP13 の義務だけ**に限定。`excluded_obligations` は**手で列挙せず** `coverage.yaml` から Evaluator が機械的に集める。CI 規則 5b-4 で「除外述語を持つ義務の要件に除外文があること」を検査 → [03 §1](03-test-model.md), [05 §5](05-test-definition-format.md) |
| 2 | P1 | IIP-MD01 の表と `coverage.yaml` 例が不一致 | 例を 3 義務（IdP:MUST / SP:SHOULD / `CLAIM_BASED` 条件付き MUST）に修正 → [05 §2.1](05-test-definition-format.md) |
| 3 | P1 | IIP-SP06 の「0 個」は不正な SAML | SAML Core §3.3.2.2.1 では `ClassRef`/`DeclRef` は 1 個以上。**0 個のケースを削除**し、1 個 / 複数個に。0 個は不正メッセージ生成（IIP-EXT01 / Phase 4）の領域であることを明記 → [04](04-requirement-coverage.md) |
| 4 | P1 | IIP-MD03 は 4 義務 | `.b`（検証鍵の out-of-band 設定）を `.a` の括弧書きから独立させ 4 義務に → [04](04-requirement-coverage.md) |
| 5 | P1 | `conflict=true` の不変条件が重大度順序と矛盾 | 「verdict が `INCONSISTENT` である」→「集約入力に `INCONSISTENT` が**注入されている**」に修正。検証は「重大度が `INCONSISTENT` **以上**」。同じ義務に FAIL があれば FAIL が正しい → [06 §1.2](06-results-and-publication.md) |
| 6 | P2 | G1 の `observed` 必須規則が新述語モデルと矛盾 / `source_digest` からは語を検査できない | G1 の条件を述語種別ごとに分岐。**`source_excerpt_normalized`（正規化済み原文断片）と `source_selector`** を追加し、`CLAIM_BASED` の妥当性検証（規則 5b-3）がこの断片を使うようにした → [04 G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |
| 7 | P1 | IIP-MD05 の 6 仕様と MUST NOT が未分解 | `.a`〜`.f`（6 仕様）+ `.g`（MUST NOT）に分解。**`mdrpi` を必須リストから外し**、`.g` 側の題材に回した → [04](04-requirement-coverage.md) |
| 8 | P1 | IIP-MD06 が証明書処理しか扱っていない | `.a` 任意数のピア・追加入力なし / `.b` 署名検証に別 trust store 不要 / `.c` SOAP/TLS に別 trust store 不要 に分解 → [04](04-requirement-coverage.md) |
| 9 | P1 | IIP-SSO06 が自己申告のみ | [SAML2Prof] §4.1.6 の MUST/MAY 要素を列挙し、**Suite メタデータの値を変更して追従するか**を検証する形に。要素一覧の確定は G1 → [04](04-requirement-coverage.md) |
| 10 | P1 | IIP-IDP04 の `isRequired` と Index が未分解 | `.a`（`isRequired` を含む `RequestedAttribute` に基づく判断）と `.b`（`AttributeConsumingServiceIndex` 対応）を別 MUST に。`.b` は Suite 側から自動判定できる → [04](04-requirement-coverage.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| R6 | 5 | 5 |
| **累計** | **31** | **25** |

**照合した要件の 8 割に誤りがあった。** 直近 3 回は照合した要件が**全件**誤っている。
残り 38 要件も同じ前提で扱う。

観察された誤りの類型（G1 の分解手順に反映済み）:

| 類型 | 例 |
|---|---|
| **もう 1 つの MUST が文の後半に隠れている** | G03 / MD01 / MD03 / SP06 / IDP04 / SP04 / ALG05 |
| **列挙された仕様・要素を個別義務にしていない** | MD05（6 仕様）/ ALG06（5 条項）/ SSO06（§4.1.6 の要素群） |
| **参照先仕様まで遡らないと期待値が決まらない** | SSO07（SAML2Core）/ IDP15（SAML-EC）/ SSO06（SAML2Prof §4.1.6）/ IDP13（ECP v2） |
| **要件末尾の適用除外を見落とす** | IDP13（token translation Proxy / IIP-SSO02・SSO03） |
| **条件付きであることを見落とす** | SP14〜17 / MD08 / MD01.c / SSO06 |
| **Samlier が原文にない条件・閾値を足す** | MD04.c（90 日）/ ALG05（既定が CBC）/ MD05（mdrpi）/ IDP21（文字集合） |
| **隣接する要件の内容と取り違える** | MD08 ↔ SP08 / MD06 ↔ MD12 |

### この修正で変わった前提

- **除外の範囲は要件単位**。除外述語が隣接要件に広がっていないことを CI で検査する
- **`excluded_obligations` を手で書かない**。カタログから機械的に集める
- **`source_digest` だけでは足りない**。CI が原文の語を検査できるよう
  正規化済みの原文断片を併せて保存する
- **不変条件は集約規則と矛盾しない形で書く**。「等しい」ではなく「以上」
- **Samlier が原文にないものを足していないか**も G1 のレビュー項目にする
  （不足だけでなく過剰も誤りである）

---

## R7 — 2026-08-25 参照仕様の追加照合と digest 規則の整合性レビュー

**結論**: 指摘 6 件すべて妥当だった。追加照合した 2 件（IDP16 / SP17・IDP20）に加え、
MD06 と IDP04 の修正内容そのものにも誤りがあった（**私の R6 修正が過剰だった**）。
累計の誤読率は **27/33**。

> **R5 / R6 の記録の一部は本節で上書きされます。**
> `source_excerpt_normalized` を使う方式（R6 指摘 6 の対応）は R7 で撤回し、
> `:specReconcile` が原文を取得して検査する方式に置き換えました。

### 原文照合の結果（R7）

| 要件 | 原文 | 修正前の誤り |
|---|---|---|
| **IIP-MD06.c** | *implementations **should confine themselves to supporting front-channel bindings*** — TLS の話は SAML メッセージングに TLS を使う場合の制約 | R6 で無条件 MUST にしていた。**バックチャネルを持たない実装まで FAIL にする**ところだった |
| **IIP-IDP04.a** | *including the value of the enclosed `isRequired` XML attribute* — **判断材料にできる能力**を要求するのみで、`true`/`false` の結果は規定していない | R6 で「`isRequired` を変えれば属性集合が変わる」ことを期待していた。**適合実装が両者を同じポリシーで扱うことは許される** |
| **IIP-IDP16** | [SAML2ECP] §2.3.10 の列挙: PAOS ACS / **SOAP `SingleSignOnService`** / **`cb:supportsChannelBindings`** / **HoK 対応時の `hoksso:ProtocolBinding`** / ACS の `index` `isDefault` | PAOS ACS しか扱っていなかった |
| **IIP-SP17 / IIP-IDP20** | 両者とも [SAML2Prof] **§4.4.5** を参照 | SLO エンドポイントへの追従だけ。**暗号化時の `<md:KeyDescriptor use="encryption">`** が欠落（§4.4.5 の正確な列挙は G1 で原文確認） |

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | `source_digest` の検証規則が成立しない（節の全文と省略付き断片のダイジェストが一致するはずがない） | **`source_excerpt_normalized` を廃止**。`source_selector` + `source_section_digest` のみにし、語の検査は **`:specReconcile`**（原文を `build/spec-cache/` に取得するネットワーク要のジョブ）が行う。**原文を 1 文字も配布せずに**検査でき、[09 D-11](09-open-decisions.md) とも両立する → [04 G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |
| 2 | P1 | IIP-MD06.c が無条件 MUST | `condition: uses_tls_for_saml_messaging`（`CAPABILITY_BASED`）に。front-channel のみの実装を FAIL にしない → [04](04-requirement-coverage.md) |
| 3 | P1 | IIP-IDP04.a が配備ポリシーを勝手に固定 | Samlier が結果を決めない手順に: ①対象側で「`isRequired` で差が出るポリシー」を設定してもらう ②その状態で variant を配布して差を観測 ③設定できない場合は **(a) 製品が判断材料にできない → FAIL** / **(b) 利用者が設定・確認できない → `NOT_VERIFIED`** に分岐 → [04](04-requirement-coverage.md) |
| 4 | P2 | R6 の累計値が内部で矛盾（`24/30` vs 集計表 `25/31`） | 集計表に合わせて `25/31` に訂正 |
| 5 | P1 | IIP-IDP16 が PAOS ACS だけ | §2.3.10 の 5 要素（`.a`〜`.e`）に分解。HoK は条件付き → [04](04-requirement-coverage.md) |
| 6 | P1 | IIP-SP17 / IIP-IDP20 に `KeyDescriptor` が不足 | 両者を `.a` `SingleLogoutService` / `.b` **暗号化時の `KeyDescriptor use="encryption"`**（`condition: uses_encrypted_identifiers`）に分解。§4.4.5 の正確な列挙は G1 で確定 → [04](04-requirement-coverage.md) |
| — | 補足 | MD05.g の題材を `mdrpi` だけにすると検出力が落ちる | **未知の名前空間の well-formed な拡張**を必ず併用する（実装が知りようのない要素で試す） → [04](04-requirement-coverage.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| R6 | 5 | 5 |
| R7 | 2 | 2 |
| **累計** | **33** | **27** |

★ R7 では **前回の修正内容そのもの**にも 2 件の誤りがあった（MD06.c / IDP04.a）。
いずれも**原文にない条件を Samlier が足した**類型であり、
R6 で新設したはずの「過剰も誤り」というレビュー観点が、自分の修正には効いていなかった。

**含意**: G1 のレビューは「原文にある内容が全て分解されているか」だけでなく、
**「分解した内容が全て原文にあるか」を逆向きにも確認する**必要がある。
承認チェックリストに双方向の確認を明記する。

### この修正で変わった前提

- **原文はリポジトリに置かない**。CI は `:specReconcile` で取得時に検査する。
  日常の `./gradlew check` はオフラインで完結する
- **能力の義務と結果の義務を混同しない**。「X を判断材料にできること」と
  「X が真なら Y すること」は別物であり、後者を勝手に期待値にしない
- **G1 の確認は双方向**。不足の検出だけでなく、Samlier が足した過剰の検出も行う

---

## R8 — 2026-08-25 原文根拠の検証と義務分解の粒度レビュー

**結論**: 指摘 9 件すべて妥当だった。原文照合した 6 件（G01 / G02 / SSO02 / SSO04 / MD07 / SP08・IDP19）は
**6 件とも不足または過剰があった**。累計の誤読率は **33/39**。

### 原文照合の結果（R8）

| 要件 | 原文 | 修正前の誤り |
|---|---|---|
| **IIP-G01** | *MUST allow for **reasonable** clock skew* — 3〜5 分が *reasonable default*。**上限も、許容しすぎた場合の不適合条件もない** | 「±3600 秒でも受理したら WARNING」は**原文に根拠がない**（過剰） |
| **IIP-G02** | *comprised of **any combination of valid XML characters** and contain up to 256 characters* | 長さ 256 の 1 例だけ。文字集合を検証していない。かつ `<saml:AttributeValue>` は `xs:string` とは限らない |
| **IIP-SSO02** | *MUST support the HTTP-Redirect **and** HTTP-POST bindings* — 両方への対応が義務 | SP テストが「どちらを使うか観測」だけで、**両方を発行できる能力を証明していない** |
| **IIP-SSO04** | *MUST support the signing of assertions and responses, **both together and independently*** | IdP テストが 1 構成の観測のみ。**Response 単独署名の能力を検証していない** |
| **IIP-MD07** | *MUST have the ability to consume … any number of signing keys* + *MUST attempt to use each signing key … until … verified*（**MUST が 2 回**） | 1 義務にまとめていた |
| **IIP-SP08 / IIP-IDP19** | *MUST support decryption* + *MUST be configurable with at least two decryption keys* + *MUST attempt to use each decryption key*（**MUST が 3 回**） | 1 義務にまとめていた。**IIP-SP16 では既に 3 義務に分解済みで、同じ構造なのに不統一だった** |

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | `specReconcile` が義務と原文の句を対応付けられない | 節境界の規則（次の要件アンカー直前まで）と正規化規則を明文化。**義務ごとに `source_clause`（正規化済み節内の文字オフセット範囲 + digest）** を追加し、語の検査を**句単位**で行う。節全体で検査すると同じ節の別義務での誤用を見逃す → [04 G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |
| 2 | P1 | IIP-G01 に原文にない上限警告 | **Advisory の仕組みを新設**。`affects_verdict: false` をスキーマで固定し、Verdict・coverage・conformance のいずれにも影響させない。CI 規則 24b / 不変条件 9g で強制 → [04 §Advisory](04-requirement-coverage.md), [06 §1](06-results-and-publication.md), [09 D-14](09-open-decisions.md) |
| 3 | P1 | IIP-G02 の試験範囲が不足 | 255/256 の境界・非 ASCII・結合文字・サロゲートペア・改行/タブの variant を追加。対象を**型が `xs:string` であることが明確なフィールド**（`@ProviderName` / `@Name` / `@FriendlyName`）に限定。切り詰めは元値との一致で確認 → [04](04-requirement-coverage.md) |
| 4 | P1 | IIP-SSO02 の SP 試験が片方しか検証しない | Suite メタデータの `SingleSignOnService` を Redirect のみ / POST のみにした **2 構成で SP に発行させる** → [04](04-requirement-coverage.md) |
| 5 | P1 | IIP-SSO04 の IdP 試験に検出力がない | IdP 側でも **(a) Assertion のみ (b) Response のみ (c) 両方**の 3 構成を生成できることを確認。`WantAssertionsSigned` では Response 単独署名を検証できない → [04](04-requirement-coverage.md) |
| 6 | P1 | IIP-MD07 が 2 つの MUST に分解されていない | `.a`（任意個数の鍵を消費）/ `.b`（成功まで各鍵を試す）に分解 → [04](04-requirement-coverage.md) |
| 7 | P1 | IIP-SP08 / IIP-IDP19 も MUST ×3 | 両者を `.a` 復号 / `.b` 2 鍵以上設定可能 / `.c` 各鍵を順に試す に分解。IIP-SP16 と構造を揃えた → [04](04-requirement-coverage.md) |
| 8 | P2 | MD06.c の観測条件が TLS 利用を証明しない | 観測材料を **`https:` の SOAP エンドポイント**または**実際の TLS 上の SOAP 通信**に限定。「SOAP エンドポイントがある」だけでは TRUE にしない → [04](04-requirement-coverage.md) |
| 9 | P2 | `specReconcile` がリリースの構造的ゲートになっていない | `release` / `publish` / `dockerPush` が `:specReconcile` に **`dependsOn`** し、**その実行で生成されたレポートのみ**を受け付ける（定期ジョブの結果を流用しない）。CI 規則 29 → [04 G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| R6 | 5 | 5 |
| R7 | 2 | 2 |
| R8 | 6 | 6 |
| **累計** | **39** | **33** |

### 新しく分かったこと

**同じ構造の要件で分解の粒度が揃っていない**という類型が加わった。
IIP-SP16 は 3 義務に分解済みなのに、**構造が同一の IIP-SP08 / IIP-IDP19 は 1 義務**だった。
G1 のチェックリストに追加する:

> 同じ言い回し（*configurable with at least two … keys* / *attempt to use each … until*）を
> 持つ要件どうしで、**分解の粒度が揃っているか**を横断的に確認する。

**Advisory の新設**により、「実務的に伝えたいが原文に根拠がない」観測の逃げ場ができた。
R5・R7・R8 で指摘された過剰（MD04.c の 90 日 / ALG05 の CBC 既定 / G01 の上限）は
すべてこの類型であり、判定から外して advisory に移した。
今後「これは伝えたい」と思ったときは、**まず原文に根拠があるかを確認し、
なければ Verdict ではなく advisory にする**。

### この修正で変わった前提

- **語の検査は句単位**。節単位では同じ節の別義務での誤用を見逃す
- **原文に根拠のない観測は advisory**。`affects_verdict: false` をスキーマで固定する
- **リリースは `:specReconcile` に構造的に依存する**。運用規約に頼らない
- **同じ言い回しの要件は分解の粒度を揃える**

---

## R9 — 2026-08-25 能力欠如の判定と検出力のレビュー

**結論**: 指摘 7 件すべて妥当だった。原文照合した 4 件（SSO03 / ALG04 / IDP06 / SP07）は
**4 件とも不足があった**。累計の誤読率は **37/43**。

### 原文照合の結果（R9）

| 要件 | 原文 | 修正前の欠落 |
|---|---|---|
| **IIP-SSO03** | *HTTP-POST binding for authentication **and error** responses* | エラー応答が欠落 |
| **IIP-ALG04** | **2 つの URI**（AES128-GCM / AES256-GCM）を列挙 | 単一の「GCM で送る」ケース。**片方だけ対応する実装が PASS する** |
| **IIP-IDP06** | + *authentication mechanisms … **MUST have access to the ForceAuthn indicator** so that their behavior may be influenced by its value* | 2 つ目の MUST が欠落 |
| **IIP-SP07** | *MUST support the **acceptance or rejection** of assertions based on … `<saml:AuthnContext>`* | 拒否ケースのみ。**全 Assertion を拒否する実装も PASS する** |

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | 「製品に能力がない」と「検証者が設定できない」の混同が再発 | ★ **共通判定手順を [03 §4](03-test-model.md) に新設**。`CONFIG` の全ケースが通る 3 分岐（能力なし → **`FAIL(capability_absent)`** / 権限・環境 → `NOT_VERIFIED(target_config_unavailable)` / 判別不能 → **`NOT_VERIFIED(capability_undetermined)`**（新設 reason））。質問文は Runner が共通に出す。CI 規則 20b（`CapabilityBranchTest`）と 20c、不変条件 8b で強制。**個別要件に書き分けていたため取りこぼした**ので、6 箇所を共通手順への参照に統一 → [04](04-requirement-coverage.md) |
| 2 | P1 | IIP-G02 の改行・タブと文字数の扱いが未定義 | **(1)** 属性値中のリテラル TAB/LF/CR は [XML 属性値正規化](https://www.w3.org/TR/xml/#AVNormalize)で空白になるため、**XML ソース文字列との一致を要求すると適合実装を誤判定する**。リテラル版と**文字参照版**（`&#x9;` 等）を別ケースに **(2)** 「サロゲートペア」は UTF-16 の表現であって文字種ではない。**補助平面のコードポイント**を含める／**孤立サロゲートを生成しない**／長さは **Unicode コードポイント数**、と定義し直した → [04](04-requirement-coverage.md) |
| 3 | P1 | IIP-SSO03 のエラー応答が未テスト | `.a` 認証応答 / `.b` エラー応答 に分解。SP 側は「エラー Response を成功扱いしないこと」、IdP 側は IIP-IDP05 と対で検証 → [04](04-requirement-coverage.md) |
| 4 | P1 | IIP-ALG04 が 2 アルゴリズムを個別検証していない | `.a` AES128-GCM / `.b` AES256-GCM に分解。送信側・受信側の両方向 → [04](04-requirement-coverage.md) |
| 5 | P1 | IIP-IDP06 の 2 つ目の MUST が未分解 | `.b`（認証機構が `ForceAuthn` にアクセスでき動作を変える）を追加。**省略 / `false` / `true` の 3 対照**を `AuthnInstant` の比較で部分自動判定 → [04](04-requirement-coverage.md) |
| 6 | P1 | IIP-SP07 が拒否ケースだけで検出力がない | **同一設定下で受理・拒否を対にする**。一致 ClassRef の受理と不一致 ClassRef の拒否の**両方が成立して初めて PASS** → [04](04-requirement-coverage.md) |
| 7 | P2 | `source_clause` のオフセット規約が不完全 | **0-based / end-exclusive / Unicode コードポイント単位 / 空範囲禁止 / digest は切り出し文字列の UTF-8 バイト列の SHA-256** と固定。範囲・単位の制約はオフライン（CI 規則 6c-0）、digest の一致は `:specReconcile` で検証 → [04 G1](04-requirement-coverage.md), [05 §5](05-test-definition-format.md) |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| R6 | 5 | 5 |
| R7 | 2 | 2 |
| R8 | 6 | 6 |
| R9 | 4 | 4 |
| **累計** | **43** | **37** |

### 新しく分かったこと

**「検出力のないケース」が繰り返し出ている。**

| 回 | 要件 | 何が起きていたか |
|---|---|---|
| R5 | IIP-SSO07 | 「エラーも無視も可」— どちらでも PASS |
| R8 | IIP-SSO02 / SSO04 | 片方の構成しか見ない — 片方だけ対応でも PASS |
| R9 | IIP-ALG04 | 片方のアルゴリズムしか試さない |
| R9 | IIP-SP07 | 拒否だけ — 全部拒否する実装も PASS |

共通するのは **「対照（negative control）がない」**こと。
G1 のチェックリストに追加する:

> 各ケースについて、**「この期待値を満たすが義務は満たしていない実装」が存在しないか**を
> 考える。存在するなら対照ケースが要る。
> - 列挙された選択肢は**すべて**個別に試す（ALG04 / ALG06 / MD05 / SSO02）
> - 「受理または拒否」の義務は**受理と拒否の両方**を対にする（SP07）
> - 「A または B でよい」ケースは verdict を付けない（SSO07）

**さらに、判定分岐を要件ごとに書き分けると必ず取りこぼす。**
R9 の指摘 1 は R7 で IDP04 と MD04.c にだけ入れた分岐が、
他の 6 要件に展開されていなかったもの。
**判定に関わる規則は要件表ではなく [03](03-test-model.md) に置き、要件表からは参照する。**

### この修正で変わった前提

- **能力の欠如は FAIL**。`NOT_VERIFIED` は「製品は適合しているかもしれないが証拠がない」場合に限る
- **判定規則は要件表に書かない**。共通手順に置き、CI で全ケースが通ることを保証する
- **対照のないケースは作らない**。「満たすが適合していない実装」が作れるなら検出力がない
- **XML の正規化を前提に期待値を決める**。ソース文字列との一致は誤判定を生む

---

## R10 — 2026-08-25 共通化した判定規則の回帰レビュー

**結論**: 指摘 8 件すべて妥当だった。原文照合した 4 件（EXT01 / SP05 / IDP18 / G02）は
**4 件とも不足**。累計の誤読率は **41/47**。

**R9 で共通化した判定手順そのものが、R2 で潰した誤りを再発させていた。**
これが本レビューで最も重い所見である。

### 反映結果

| # | P | 指摘 | 対応 |
|---|---|---|---|
| 1 | P1 | `capability_absent → FAIL` の共通規則が判定モデルを破っている | ★ **共通手順を `outcome` ベースに書き直した**。ケースは `outcome: violated` + `reason_code: capability_absent` を返し、**Verdict への変換は Evaluator が `obligation.level` を見て行う**（MUST→FAIL / **SHOULD→WARNING** / MAY→NOT_SUPPORTED）。加えて `configuration_failure_semantics`（`normative_capability` \| `test_precondition`）を必須フィールドに追加 — `CONFIG` は実行方式であって「設定能力が規範要件か」を表さない。IIP-ALG05.b のような条件付き義務は、**適用性の評価がケース実行より先**であることも明記（CBC 非対応なら `NOT_APPLICABLE`。FAIL でも WARNING でもない） → [03 §4](03-test-model.md), [05 §2.3, §5](05-test-definition-format.md), [06 §1.2](06-results-and-publication.md) |
| 2 | P1 | 共通判定への移行が未完了（IDP04・D-14 に旧 2 分岐、IDP19 に参照なし） | 3 箇所を共通手順への参照に統一。IDP19 の testability を `B/C` に修正 |
| 3 | P1 | IDP06 の対照ケースが適合実装を落とす | SAML Core が禁じるのは **`true` のときに既存コンテキストに依拠すること**だけ。`false`／省略時の自主的な再認証は**禁止されていない**。→ Verdict の対象を「`true` のときに新規認証が行われた証拠」に限定し、`false`／省略時の挙動は advisory `force_authn.reauth_when_not_requested` に |
| 4 | P1 | EXT01 に `xsd:anyAttribute` の試験がない | `.c`（`xsd:anyAttribute` を持つ要素への未定義属性）を追加。`<samlp:Extensions>` / `<md:Extensions>` / `<saml:Advice>` を個別 variant に。**判定対象は「障害を起こさないこと」のみ**（無視は許されるので、内容が反映されないことを FAIL にしない） |
| 5 | P1 | SP05 に同一リソース URL の対照がない | 原文は *MUST NOT be a restriction … requiring **distinct resource URLs** for each IdP*。→ **同一の保護リソース `R`** に対して IdP A / IdP B の両方から到達できることを対にする。第 2 IdP を登録するだけでは不適合実装も PASS する |
| 6 | P1 | IDP18 が未分解・テスト欄が空 | *for logout requests **and** responses* に従い `.a` LogoutRequest / `.b` LogoutResponse に分解。生成方向と消費方向の両方を見る |
| 7 | P2 | G02 に XML 構文上特別な文字がない | `<` `&` `"` `'` `>` を文字参照・エンティティ参照で渡し、解析後の値として保持されるケースを追加 |
| 8 | P2 | `source_clause` の節長をオフライン検証できない | 原文をリポジトリに置かない設計なので節長を知りようがない。オフライン（規則 6c-0）は `0 ≤ start < end` と非空のみ、**`end ≤ 節長` は `:specReconcile`（6c-1）へ移動** |

### 累計の誤読率

| 回 | 照合数 | 誤り |
|---|---|---|
| R1 | 9 | 5 |
| R2 | 8 | 6 |
| R4 | 3 | 3 |
| R5 | 6 | 6 |
| R6 | 5 | 5 |
| R7 | 2 | 2 |
| R8 | 6 | 6 |
| R9 | 4 | 4 |
| R10 | 4 | 4 |
| **累計** | **47** | **41** |

### ★ 最も重い所見: 共通化そのものが回帰を生んだ

R9 の指摘 1 は「判定分岐を要件ごとに書くと取りこぼす」だった。
その対策として共通手順を作ったが、**その共通手順が
「ケースは Verdict を返さない」という R2 で確立した設計を破っていた**。
結果、SHOULD 義務を FAIL にする経路が**全 `CONFIG` ケースに一斉に**開いた。

個別の誤りより影響範囲が広い。**共通化するときは、既存の不変条件を壊していないかを
必ず確認する**。G1 のチェックリストに追加する:

> 判定に関わる規則を追加・共通化するときは、
> [03 §4 の判定語彙](03-test-model.md) と [05 §2.3 の変換表](05-test-definition-format.md) を
> 経由しているかを確認する。**Verdict を直接生成する経路を作らない。**

### 修正回数について

R1 から R10 まで 10 回のレビューで、要件表の記述は延べ 80 箇所以上修正した。
原因は明確で、**私が原文を 1 節ずつ読まずに要件表を書き進めたこと**である。
IIP の要約（初回に取得した 1 行サマリ）を根拠にしたため、
「文の後半に隠れた 2 つ目の MUST」「条件付き」「適用除外」「列挙」を
系統的に落とした。**照合した 47 件のうち 41 件が誤っている**。

この状態の要件表を土台に実装を始めれば、
仕様準拠の実装を FAIL と表示するツールになる。
[設計ゲート G1](04-requirement-coverage.md) —
**原文を 1 節ずつ読み切って `coverage.yaml` を起こし、作成者以外が原文を直接読んで承認する** —
を通すまで実装に入らない方針を維持する。
現在の要件表は G1 の**入力メモ**であって、成果物ではない。

### この修正で変わった前提

- **共通化しても Evaluator を経由する**。Verdict を直接生成する経路を作らない
- **`CONFIG` は実行方式であって規範性を表さない**。`configuration_failure_semantics` で明示する
- **適用性の評価はケース実行より先**。条件が偽の義務が判定手順に入ってはならない
- **「禁止されていないこと」を FAIL にしない**（IDP06 の `false`／省略時の再認証）
- **オフラインで検証できない制約をオフライン規則に書かない**

---

## G1a — 2026-08-25 設計ゲート G1 の作成フェーズ

**状態: `PENDING_REVIEW`**（作成者は `reviewer` / `approved_at` を埋めていない）

### やり方を変えた点

R1〜R10 の誤りは **私が原文を読まず、初回に取得した 1 行サマリを根拠に要件表を書き進めた**ことが原因だった。
今回は経路そのものを変えた。

| 従来 | G1a |
|---|---|
| 要約サービス経由で仕様を読む | **HTML を `curl` で取得し、自分でテキスト化して全文を読む** |
| 要件表（Markdown）に直接書く | **義務単位の構造化データ**に分解し、Markdown は生成物にする |
| 判定レベルを記憶と要約から決める | **原文の句を特定し、オフセットとダイジェストで固定する** |
| 規範/非規範を区別していなかった | **`<em>` = 非規範**（Notation の規定）を機械的に分離する |

### 原文から新たに判明した重要事実

Notation 節に決定的な規則がある。

> *All information within these requirements should be considered normative unless it is set in italic type.*

これに従って `<em>` スパン **26 件**を非規範として除外した結果、
**過去のレビューで私が追加した義務のうち複数が非規範テキスト由来**だったことが分かった。

| 要件 | 過去に追加した義務 | 実際 |
|---|---|---|
| **IIP-SP04** | `.b` *discovery mechanisms SHOULD use SAML metadata…*（R8 で追加） | **非規範（イタリック）** → 削除。SP04 の規範義務は 1 つだけ |
| **IIP-MD06** | `.b` `.c` trust store に関する義務（R6 で追加、R7 で条件化） | **非規範（イタリック）** → 削除。MD06 の規範義務は MDIOP 準拠・任意数ピア・自己完結性の 3 つ |
| **IIP-ALG05** | `.b` *Implementations supporting them SHOULD warn on use.*（R8 で追加） | **規範**（イタリックではない） → 維持 |
| **IIP-IDP06** | `false`／省略時の期待値（R9 で追加、R10 で撤回） | 該当箇所は**非規範** → 撤回が正しかった |

また **IIP-G02** に、これまで一度も捉えていなかった条件付きの前提があった。

> *When specific constraints are absent in the SAML standards or profile documents, …*

**SAML が長さ・文字種を制約していないフィールドにのみ適用される**という限定であり、
テスト対象フィールドの選定条件になる。`applicability_note` として記録した。

### 成果物

| ファイル | 内容 |
|---|---|
| `tests/specs.yaml` | 仕様カタログ 22 件。IETF ドラフト（SAML-EC / MDQ / SAML-MDQ）は**版を固定** |
| `tests/coverage.yaml` | **69 要件 → 127 義務**。判定レベルの唯一の出典 |
| `tests/predicates.yaml` | 条件述語 8 件（CLAIM 2 / CAPABILITY 5 / CLASSIFICATION 1） |
| `build/spec-reconcile-report.json` | 13 検査すべて PASS |
| `docs/04-requirement-coverage.md` | `coverage.yaml` からの生成物（1,687 行） |
| `tools/g1_build.py` | ビルダー。authoring 入力（原文の句を含む）は gitignore |
| `tools/ci-stages.md` | `g1Check` / `specReconcile` / `releaseCheck` の分離 |

### 内訳

```
義務 127
  MUST_CLASS (MUST / MUST NOT / REQUIRED)  111
  SHOULD_CLASS (SHOULD / RECOMMENDED)       10
  MAY_CLASS (MAY / OPTIONAL)                 6
  条件付き                                   14
IdP プロファイル  96 義務（Core 72 / Full 24）
SP  プロファイル  94 義務（Core 74 / Full 20）
Testability  AUTOMATED 8 / BROWSER 53 / ATTESTED 10 / CONFIG 55 / NOT_OBSERVABLE 1
非規範スパン 26
```

`NOT_OBSERVABLE` は **IIP-SP12.a のみ**。過去に `N` としていた IIP-G02 と IIP-IDP21 は、
原文を読み直した結果いずれも試験可能（G02 は受信側の義務、IDP21 は生成方式の設定可能性を申告で確認）と分かった。

### `:specReconcile` が自分の誤りを検出した

SR-08（除外述語を持つ義務の要件節に除外文が実在するか）が、
**私が `not_token_translation_proxy` を IIP-IDP14 / IDP15 / IDP16 にも付けていた**ことを検出した。
除外文 *This requirement does not apply to token translation Proxies.* は
**IIP-IDP13 の節にのみ**存在し、IDP14〜16 は無条件の MUST である。
これは R10 の指摘 1 とまったく同じ誤りで、**人手のレビューではなく検査が捕まえた**。
修正後 13/13 PASS。

### 未解決事項（G1b で確定する）

参照先仕様の該当節を読まないと義務の完全な列挙が決まらないものが 4 件ある。
`coverage.yaml` に `open_question_ja` として記録した。

| 義務 | 未解決 |
|---|---|
| `IIP-SSO06.a` | [SAML2Prof] §4.1.6 の MUST/MAY メタデータ要素の完全な列挙 |
| `IIP-SSO07.b` | [SAML2Core] が一意の結果を規定する要素の一覧（verdict 対象にできる要素） |
| `IIP-SP17.a` / `IIP-IDP20.a` | [SAML2Prof] §4.4.5 の要素の完全な列挙 |

### レビュアーへの依頼

**原文と `tests/coverage.yaml` を直接照合**してください。この Markdown の要約ではなく、
`source_clause` のオフセットが指す原文の句と義務の対応を見てください。
確認は双方向でお願いします。

- 順方向: 原文の規範内容が**すべて**義務に分解されているか（不足）
- 逆方向: 各義務が**すべて**原文に根拠を持つか（過剰）— R7 / R10 で私が繰り返した誤り

指摘は `docs/04` ではなく **`tests/coverage.yaml` 側**を直します（`docs/04` は生成物です）。
「1 回で終わる」とは想定していません。

---

## G1a-R1 — 2026-08-25 G1 作成フェーズへの「Changes requested」

**結論**: 指摘 9 件すべて妥当。うち 2 件は原文を再確認した結果、
**レビュアーの列挙自体が不完全**だったため、原文どおりに拡張した。
累計の誤読率は **41/49**。

### 最も重い所見: 「13/13 PASS」が自己検証だった

`g1_build.py` が**生成と検証を同時に行っていた**ため、SR-01 は取得した本文の
ダイジェストをその場で `specs.yaml` に書いており、**原文が変わっても必ず PASS** した。
`find_clause()` も最初の一致しか返さず、複数一致を検査していなかった。

→ **`tools/g1_validate.py` を独立させた**。コミット済みの成果物を読み込んで照合するだけで、
一切の値を書き戻さない。分離した直後、validator が

> `IIP-EXT01.b` と `IIP-EXT01.c` が**同じ文字列**（`but MUST NOT result in software failures`）を
> 指しており、`.c` は誤った出現位置を参照していた

ことを検出した。**生成と検証が同じコードだった間は気づけなかった実バグ**である。
句を一意なものに直し、複数一致は既定でエラー（`occurrence=` の明示を要求）にした。

### 反映結果

| # | 指摘 | 対応 |
|---|---|---|
| 1 | 検証が独立していない / SR-01・09・13 が実質固定値 / 複数一致を見ない | `g1_validate.py` を新設（32 検査）。SR-01 は**取得値と記録値の比較**に、SR-09 は `predicates.yaml` の実検査に、SR-13 は句単位の語検査に変更。SR-11/SR-12 で出現回数と曖昧さを検出 |
| 2 | 規範内容の未分解 4 件 | **MD03.e**（鍵は X.509 に格納してもよい・MAY）、**SP02.c**（complex content は OPTIONAL）、**IDP13 を .a/.b/.c/.d に分割**（ECP MUST / Full conformance OPTIONAL / Bearer MUST / channel bindings MUST）、**EXT01.b1・c1**（無視してよいという MAY を MUST_NOT から分離）を追加。**127 → 133 義務**、MAY_CLASS **6 → 11** |
| 3 | 未解決 4 件が残っており G1 未完了 | **4 件すべて解消**（下記）。SR-30 を「未解決が残っていれば FAIL」に変更し、記録されていれば PASS という以前の甘い判定をやめた |
| 4 | review スキーマが設計文書と不一致 | `authored_by` と `review.{source_spec, spec_version, source_selector, source_section_digest}` を追加。SR-25 / SR-26 で検査。docs/05 の規則 6b・6c も実体に合わせた |
| 5 | `configuration_failure_semantics` に暗黙の既定値 | 自動補完を**撤去**し、`CONFIG` の全 56 義務を明示分類（`normative_capability` 36 / `test_precondition` 20）。未指定は builder が assert で落ちる。SR-19 でも検査 |
| 6 | outbound 暗号化の適用性が誤検出 | 観測を**方向付き**に変更。`target_emitted: saml:EncryptedAssertion` 等のみを証拠とし、`md:KeyDescriptor[@use='encryption']`（受信側の証拠）を除外。他の述語も `target_emitted` / `target_consumed` / `target_accepted` に統一 |
| 7 | 再生成性が成立していない | 絶対パスを撤去。**`g1_docgen.py`（coverage.yaml → docs/04、ネットワーク・authoring 入力とも不要）**を分離し、`--check` で一致を検証できるようにした。別 checkout で再生成・検証が完結する |
| 8 | `source_clause` が単一範囲 | **`source_clauses[]`（複数範囲）**に変更。共有 lead-in と個別 item を別範囲で持てる。`occurrences` も記録 |
| 9 | ALG07 の参照文書が specs にない | **RFC7457** と **BetterCrypto** を追加。BetterCrypto は版のない生きた文書なので `referenced-unversioned` とし「判定の根拠には使わない」と明記。SAML2Prof / SAML2Core / Errata 05 の実測ダイジェストも記録 |

### 未解決 4 件の解消（原文を直接読んだ）

| 義務 | 解消内容 |
|---|---|
| **IIP-SSO06.a** | [SAML2Prof] §4.1.6 を PDF から読んで列挙。**レビュアーの 4 系統は不完全**で、実際は `WantAuthnRequestsSigned` / `AuthnRequestsSigned` / `KeyDescriptor use=signing` / `use=encryption` に加え、**`WantAssertionsSigned`（MAY）**、**`ArtifactResolutionService`（条件付き MUST）**、**`NameIDFormat` / `AttributeProfile` / `saml:Attribute`（MAY）**、**`AttributeConsumingService` と `@index` / `@isDefault`（MAY）** がある。Errata 05 **E58** で `sign`→`signing`、`encrypt`→`encryption` を確認 |
| **IIP-SP17.a / IIP-IDP20.a** | §4.4.5 は **`md:SingleLogoutService`** と、**識別子を暗号化する場合の `md:KeyDescriptor use=encryption`** の 2 件のみ。レビュアーの指摘どおり |
| **IIP-SSO07.b** | [SAML2Core] §3.4.1 / §3.4.1.4 を読んで**判定規則を確定**。`<saml:Subject>` は *the resulting assertions' `<saml:Subject>` **MUST strongly match*** ／ 認識できなければ ***MUST return** a `<Response>` with an error `<Status>`* で**一意** → verdict 対象。`<saml:Conditions>` は *The responder **MAY** modify or supplement* で一意でない → 情報記録のみ。無効な `AssertionConsumerServiceIndex` は ***MAY** return an error `<Response>` or it **MAY** use the default location* と**二択が明示**されている → 情報記録のみ |

### 現在の数値

```
要件 69 / 義務 133
  MUST_CLASS 112 / SHOULD_CLASS 10 / MAY_CLASS 11 / 条件付き 16
IdP 101 義務 / SP 98 義務
specReconcile  31/32 PASS  ブロッキング 0
  残り 1 = SR-31「全 obligation が APPROVED」（作成フェーズなので当然 FAIL）
open question  0
```

### 役割分担についての所見

「レビュアーと書く人を逆にすべきか」という問いへの答えは **部分的に yes、ただし単純な交換ではない**。
記録が示しているのは、私の失敗が**散文からの初回抽出**に集中し、
**機械化した工程では質が変わる**ことである（G1a で SR-08 が、G1a-R1 で SR-12 が、
いずれも私自身の誤りを検出した）。逆にレビュアーの指摘は毎回**意味判断**で正確である。

したがって適切な分担は「交換」ではなく **工程による分割**:

- 私: 機械化できる部分（抽出・オフセット・ダイジェスト・validator・生成物・CI 規則）
- レビュアー: 意味判断（句と義務の対応、規範/非規範、過不足）

今回の指摘 1（validator の独立化）はまさにこの分割を強化するものだった。
ただし **G1a-R1 で 2 件はレビュアーの列挙も不完全だった**（§4.1.6 の要素、
`configuration_failure_semantics` の分類方針）ので、意味判断側も一次資料に当たる前提は残る。

### 再提出

全 133 義務を `PENDING_REVIEW` のまま再提出する。
`reviewer` / `approved_at` は引き続き未設定。指摘は `tests/coverage.yaml` 側を直す。

---

## G1a-R2 — 2026-08-26 承認ゲートと参照仕様検証のレビュー

**結論**: 指摘 8 件すべて妥当。修正の過程で、**私が入れたはずの承認ゲート SR-36 が
無言で挿入に失敗しており、実際には存在しなかった**ことが判明した。

### 承認ゲートは実在しなかった（指摘 1）

SR-31 は `state` しか見ておらず、`state: APPROVED` に一括置換するだけで通った。
SR-36 を追加したつもりだったが、置換対象が一致せず**コードに入っていなかった**
（`grep -c "SR-36" → 0`）。「ゲートがあるように見えて何もしない」典型である。

明示的に追加したうえで、**3 つの偽装パターンで実地試験**した。

| 偽装 | 結果 |
|---|---|
| `state: APPROVED` に一括置換（reviewer / approved_at は null） | ✅ **BLOCK**（SR-36: reviewer 未設定 / approved_at 未設定） |
| reviewer / approved_at も埋めるが `reviewer == authored_by` | ✅ **BLOCK**（SR-26 と SR-36） |
| 別人を reviewer にしたうえで承認時の節ダイジェストを改竄 | ✅ **BLOCK**（SR-36: 現在値と不一致） |

`g1.complete` の判定式も
`blocking failure 0 AND open question 0 AND 全 APPROVED` に修正した
（従来は blocking failure を見ておらず、構造欠陥があっても `ready_for_approval: true` になりえた）。

### 反映結果

| # | 指摘 | 対応 |
|---|---|---|
| 1 | 承認ゲートを `state` だけで通過できる / `g1_ready` が blocking を見ない | **SR-36 を実装**（reviewer・approved_at・reviewer≠author・spec/version/selector/節digest の現在値一致）。`g1.complete` を 3 条件の積に。**偽装 3 パターンで実地試験済み** |
| 2 | 参照仕様の digest が検証対象外 | **SR-32〜SR-35 を追加**。使用する全参照仕様（**18 件を実測して固定**）を validator が取得し digest を照合。さらに **`reference_evidence`**（spec / locator / 節digest）を義務に持たせ、**参照節を再抽出して digest を照合**する。共有モジュール `tools/g1_extract.py` に正規化・節切り出しを一元化し、生成側と検証側が同じ文字列に到達することを保証した |
| 3 | `source_clauses[]` が実質未実装（全 133 件が 1 範囲） | 複数範囲を実装。**25 義務が複数範囲**になった。共有 lead-in（`Implementations MUST support … the following:` など）と個別 item を別範囲で保持する。MD04 / MD05 / MD06.c / MD12 / SSO05 / ALG01〜06 / ALG08 が該当 |
| 4 | `open question = 0` が正しくない（SSO06.a に確認事項が残存） | **§4.1.6 を再読**し、RFC2119 キーワードを伴わない `md:SingleSignOnService` / `md:AssertionConsumerService` を**対象外**と明記（IIP-SSO06 の条件 (a) は「MUST/MAY と示された要素」）。`@index` / `@isDefault` も個別に MAY とはされていないため `md:AttributeConsumingService` に畳んだ |
| 5 | SSO07.b が閉じていない | **SAML2Core を横断調査**した。原文冒頭 *"Unless specifically called out by subsequent requirements in this profile"* により、他の IIP 要件が扱う要素（NameIDPolicy→IDP10 / RequestedAuthnContext→IDP08 / ForceAuthn→IDP06 / IsPassive→IDP07 / ACS 属性→IDP12 / ACSIndex→IDP04.b / Extensions・Advice→EXT01）は**対象外**。残るのは `<saml:Subject>` / `<saml:Conditions>` / `<Scoping>`系 / `ProviderName` / `Consent` で、**一意の処理規則を持つのは `<saml:Subject>` だけ**であることを §3.4.1 / §3.4.1.4 / §3.4.1.2 の引用つきで示した |
| 6 | クリーン環境で動かない / flow mapping の未引用 `sha256:` | **`tools/requirements.txt`**（PyYAML 6.0.2 / pdfminer.six 20240706）と repo-local `.venv` の手順を追加。YAML エミッタを**保守的な引用**に変更し、flow mapping 内の未引用 `sha256:` は **0 件**になった |
| 7 | docs/01 が 127 義務のまま | 133 に修正。README / ci-stages の数値も更新 |
| 8 | コミット済みでない | 未対応。**[CLAUDE.md の恒久ルール](../CLAUDE.md)により、コミットは利用者の明示指示があるまで実施しない**。レビュー対象を固定するためのコミットが必要なら指示をいただきたい |

### 参照仕様の固定（18 件）

`specs.yaml` の全参照仕様に実測ダイジェストを記録し、validator が毎回取得して照合する。

```
SAML2Core SAML2Prof SAML2Meta SAML2Errata SAML2MD-xsd SAML2-xsd SAML2MDIOP
SAML2MetaAlgSup SAML2ECP MetaUi MetaAttr SAML2ASLO MDQ SAML-MDQ IdPDisco
SAML-EC RFC2617 RFC4051 RFC7457 XMLSig XMLEnc
```

BetterCrypto は版のない生きた文書なので `referenced-unversioned` とし、
**判定の根拠には使わない**ことを明記（SR-32 の対象外）。

### 現在の状態

```
要件 69 / 義務 133（うち 25 が複数範囲の source_clauses）
参照根拠つき義務 10 / 固定した参照仕様 21
specReconcile  36/37 PASS  ブロッキング 0
  残り 1 = SR-31「全 obligation が APPROVED」— 作成フェーズなので FAIL のまま
open question  0
g1.complete    false（未承認のため。判定式 = blocking 0 AND open 0 AND 全 APPROVED）
```

全 133 義務を `PENDING_REVIEW` のまま再提出する。

---

## G1a-R3 — 2026-08-26 承認の固定・集合検査・取得セマンティクスのレビュー

**結論**: 指摘 4 件すべて妥当。3 件の再現手順はいずれも私の環境で再現し、修正後にブロックされることを実地確認した。
加えて修正の過程で、**pin していた 4 仕様の digest が再取得で再現しない**（動的ページ）ことが判明した。

### 反映結果

| # | 指摘 | 対応 | 実地試験 |
|---|---|---|---|
| 1 | 承認後に義務内容を改変できる | **`obligation_digest`** を新設。判定に影響する 16 フィールド（level / roles / condition / testability / source_clauses / required_variants / controls / reference_evidence など）を正規化 JSON 化して SHA-256。`review.obligation_digest` に保存し、**SR-25c**（承認前でも改変を検出）と **SR-36**（承認根拠）の両方で現在値と照合 | 全承認後に `IIP-G01.a` の `level` を MUST→OPTIONAL に改変 → **BLOCK**（SR-25c / SR-36） |
| 2 | 69 要件の「集合」を検査していない | **SR-02b**（coverage の要件 ID 集合 == 原文ラベル集合の完全一致）、**SR-03b**（要件 ID の一意性）、**SR-03c**（obligation key が親要件 ID + `.` で始まる）、**SR-03d**（suffix が `[a-z][0-9]?`）を追加 | `IIP-G02` を削除し `IIP-G01` を重複させ key を `IIP-G01.z` に → **8 件が BLOCK** |
| 3 | network モードでも新規取得していない | `fetch()` に **`mode`** を導入。**既定 `network` は必ず再取得**、`offline` はキャッシュのみ、`cache-first` は起票専用。validator の既定を `network` に | 全 URL を到達不能に変更して network 実行 → **BLOCK**（SR-00 / SR-33 / SR-34） |
| 4 | reference_evidence の完全性が手書き集合依存 / 取得対象が説明と不一致 | `DERIVED` のハードコードを撤去し、義務側の **`reference_derivation: true/false`** から導出（**SR-35 / 35b / 35c**）。取得対象を「使用中の仕様」から**カタログ全 22 仕様**に拡大し、`SAML2Bind` の digest も実測して固定（**SR-32 / 32b**） | `IIP-SP06.b` の `reference_evidence` を削除 → **BLOCK**（SR-35b / SR-25c） |

### 追加で判明: pin した digest が再現しない仕様が 4 件あった

指摘 3 を直して強制再取得にした結果、**同じ URL を 2 回取得してもバイト列が一致しない**仕様が出た。

| 仕様 | 旧 URL | 問題 | 対応 |
|---|---|---|---|
| MDQ / SAML-MDQ / SAML-EC | `tools.ietf.org/html/draft-…` | 動的レンダリングで毎回異なる | **`www.ietf.org/archive/id/draft-….txt`**（不変アーカイブ）へ |
| SAML2Errata | `…/errata05/os/saml-v2.0-errata05-os.html` | 同上 | **同 os の PDF** へ |

digest が再現しない参照は、**SR-33 を恒常的に落とすか、無効化されるかのどちらか**にしかならない。
`G1_VERIFY_STABILITY=1 python3 tools/g1_author.py` を追加し、
**pin する全仕様を 2 回取得して一致することを起票時に検証**するようにした
（不安定な URL は pin できない）。現在は **全 22 仕様が `stability: all pinned sources reproducible`**。

### 現在の状態

```
要件 69 / 義務 133（うち 25 が複数範囲の source_clauses）
参照根拠つき義務 10 / 固定した参照仕様 22（全て再取得で再現）
specReconcile  45/46 PASS  ブロッキング 0（network モード）
  残り 1 = SR-31「全 obligation が APPROVED」— 作成フェーズなので FAIL のまま
open question  0
g1.complete    false（判定式 = blocking 0 AND open 0 AND 全 APPROVED）
```

承認ゲートが塞いだ攻撃（累計 6 パターン、すべて実地試験済み）:

1. `state: APPROVED` に一括置換（reviewer / approved_at 未設定）
2. reviewer / approved_at を埋めるが `reviewer == authored_by`
3. 承認時の**節** digest を改竄
4. **承認後に義務の level / 要約を改変**
5. 要件の重複・欠落・key の付け替え
6. **reference_evidence の削除**

全 133 義務を `PENDING_REVIEW` のまま再提出する。

---

## G1a-R4 — 2026-08-26 承認の外部拘束・digest 範囲のレビュー

**結論**: 指摘 5 件すべて妥当。4 件の再現手順は私の環境でも再現し、修正後にブロックされることを確認した。

### 最も本質的な指摘 1: YAML 内の承認は書込者自身に対しては無力

`obligation_digest` は**事故による改変**を検出するが、**digest を計算し直せる者**は
承認情報も digest も自分で書ける。実際、`authored_by` を削除し
`reviewer: fabricated-reviewer` / `approved_at: not-a-date` を入れて digest を再計算すると
`46/46 PASS / complete=true` になった。

→ **承認の根拠を VCS 側に移した**（**SR-38**）。`APPROVED` が 1 件でもあれば
`coverage.yaml` に `approval: {commit, method, reviewers, evidence_url}` を要求し、
validator が git に対して **HEAD の一致 / `tests/` `tools/` が clean / ファイルが管理下にある**
ことを確認する。併せて **SR-25d**（`authored_by` 必須）と **SR-37**（`approved_at` の ISO-8601 検証）を追加。

**現在 `git HEAD` が存在しないため、SR-38 は APPROVED を一切許可しない。**
これは正しい振る舞いであり、同時に「G1b の開始には承認対象を固定する commit が要る」という
指摘への回答でもある。

### 反映結果

| # | 指摘 | 対応 | 実地試験 |
|---|---|---|---|
| 1 | 承認情報を自己発行できる | **SR-38**（承認を commit に拘束）+ **SR-25d**（authored_by 必須）+ **SR-37**（approved_at の形式） | authored_by 削除 + 架空 reviewer + 不正 approved_at + digest 再計算 → **3 件 BLOCK** |
| 2 | predicates.yaml が承認対象外 | 義務の正規形に**参照する述語の定義そのもの**を埋め込む（述語名だけでは observed を書き換えても digest が変わらない）。加えて **`catalog_digest`**（specs.yaml + predicates.yaml 全体）を coverage.yaml に刻み **SR-25a** で照合 | `supports_outbound_encryption.observed` を受信側証拠に改変 → **BLOCK**（SR-25a / SR-25c） |
| 3 | reference_derivation の生成が循環 | 生成側の推測を撤去し、**authoring 入力での明示を必須**に（未指定・矛盾は生成時に SystemExit）。さらに **`false` にも理由（`reference_derivation_note`）を必須**にして「黙って false にする」を有償化（**SR-35d**） | 根拠を削除し false に + digest 再計算 → **BLOCK**（SR-35d） |
| 4 | 日本語成果物が digest 対象外 | **列挙方式をやめ、`review` 以外の全フィールド**を digest 対象に。フィールドを増やしても取りこぼさない | 承認後に `summary_ja` を誤った実装指示に差し替え → **BLOCK**（SR-25c / SR-36 / SR-38） |
| 5 | stability キャッシュが残る | `_p`（publisher）ではなく **2 回目の `fetch()` が返す path** を削除。`__stab` 残存 **0 件** | 確認済み |

### digest の範囲を「列挙」から「除外」に変えた理由

指摘 4 の根本は、`JUDGMENT_FIELDS` を**列挙**していたことにある。
列挙方式は新しいフィールドを足すたびに取りこぼす（実際 `summary_ja` / `notes_ja` /
`applicability_note_ja` が漏れ、`docs/04` に出る日本語を承認後に書き換えられた）。

**`review` 以外の全フィールド**を対象にすることで、今後フィールドを増やしても自動的に保護される。

### 現在の状態

```
要件 69 / 義務 133（うち 25 が複数範囲）／ 参照根拠つき 10 ／ 固定した参照仕様 22
specReconcile  50/51 PASS  ブロッキング 0（network / offline とも）
  残り 1 = SR-31「全 obligation が APPROVED」— 作成フェーズなので FAIL のまま
open question 0 ／ catalog_digest 記録済み ／ __stab 残存 0
g1.complete    false
```

承認ゲートが塞いだ攻撃は累計 10 パターン（すべて実地試験済み）:

1. `state: APPROVED` に一括置換 / 2. 自己承認 / 3. 節 digest 改竄 /
4. 承認後の level 改変 / 5. 要件の重複・欠落 / 6. 根拠の削除 /
7. **authored_by 削除 + 架空 reviewer + 不正日付 + digest 再計算** /
8. **predicates.yaml の observed 改変** / 9. **reference_derivation の黙った false 化** /
10. **承認後の日本語説明の差し替え**

### 次に必要なこと

**承認対象を固定する commit** が要る。SR-38 は `git HEAD` がない限り APPROVED を許可しない。
コミットは [CLAUDE.md の恒久ルール](../CLAUDE.md)により利用者の明示指示があるまで行わない。

---

## G1a-R5 — 2026-08-26 承認プロトコルの再設計

**結論**: 指摘 4 件すべて妥当。指摘 1 は**私が入れた SR-38 の設計欠陥**（承認記録が承認対象の
中にある自己参照 + 前方一致による迂回）であり、承認の仕組みを作り直した。
**実 git リポジトリで正常系と改竄 8 パターンを実地試験**した。

### 指摘 1: SR-38 は正常手順では到達不能、短縮 SHA では迂回可能

私の設計は `approval.commit` を `coverage.yaml` の中に置いていた。
記録を追記した時点で対象 commit が変わるため、**正直な手順では決して一致しない**。
一方 `head.startswith(commit[:7])` だったため、1 文字の値と amend で通ってしまった。

**再設計**:

```
commit C : tests/{coverage,specs,predicates}.yaml   ← 承認対象（全て PENDING_REVIEW）
commit A : tests/approvals/g1.yaml                  ← 承認記録。C の外・署名必須
```

- `target_commit` は **40 桁完全一致**（`git rev-parse --verify <sha>^{commit}` の出力と厳密比較）
- 承認対象の内容は **`git show C:tests/*.yaml`** から読み、digest を照合
- 各義務の `obligation_digest` も**対象 commit の内容から再計算**して照合
- `evidence.ref` は**廃止**。自分を含む commit の SHA を記録に書くのは自己参照であり、
  署名し直すたびに SHA が変わる。署名済み commit は `git log -1 -- <path>` で一意に特定できる

### 指摘 2: 外部承認を実際には検証していない → 署名を必須にした

`method` / `reviewers` / `evidence_url` の非空検査は自己申告に過ぎなかった。
**`git verify-commit` による署名検証を必須**にし、さらに実地試験の途中で見つけた穴も塞いだ。

> 署名済み commit を指したまま**作業ツリーの承認記録だけ書き換える**と通過した。
> → **正本を「署名済み commit の中身」に変更**（`git show <C_sig>:tests/approvals/g1.yaml`）。
> 作業ツリーが署名済み内容と異なれば BLOCK。

**限界は明示した**: validator が保証できるのは「署名鍵の保持者が承認した」までであり、
その鍵が実在のレビュアーのものかは `allowedSignersFile` / CODEOWNERS など
リポジトリ運用側の設定に委ねられる。validator はそれ以上を主張しない。

### 指摘 3・4

- **SR-37**: `str(v)[:10]` をやめ、**文字列全体を `fromisoformat` で解析し、タイムゾーン必須**に。
  `2026-08-26garbage` は BLOCK
- **SR-39** を新設し、完了式に `coverage.g1_state == "APPROVED"` を追加

### 実地試験（実 git リポジトリ）

正常系 —— 対象 commit C を作り、承認記録を **別 commit A** に SSH 署名付きで追加:

```
50/50 PASS  blocking 0  complete = true
```

改竄 —— 承認済み状態から:

| # | 改竄 | 結果 |
|---|---|---|
| A | coverage の `level` を改変 | **BLOCK**（SR-25c） |
| B | `g1_state` を PENDING_REVIEW に戻す | **BLOCK**（SR-39） |
| C | 作業ツリーの承認記録を改変 | **BLOCK**（SR-38: 署名済み内容と不一致） |
| D | `predicates.yaml` を改変 | **BLOCK**（SR-25a / SR-25c ほか 3 件） |
| E | 承認記録を書き換えて**署名なしで amend** | **BLOCK**（署名検証失敗） |
| F | `target_commit` を別 commit にすり替え | **BLOCK** |
| G | reviewer を `authored_by` と同一に | **BLOCK** |
| H | `approved_at` を `2026-08-26garbage` に | **BLOCK** |

### 現在の状態

```
要件 69 / 義務 133 ／ 参照仕様 22 ／ 参照根拠つき 10
specReconcile  49/50 PASS  ブロッキング 0（network / offline とも）
  残り 1 = SR-31「全 obligation が承認済み」— 承認記録がまだ無い
open question 0 ／ g1.complete = false
```

累計で塞いだ攻撃は **18 パターン**（すべて実地試験済み）。

### 次に必要なこと

G1b を開始するには、**承認対象を固定する commit C** が要る。
署名済みの承認 commit A は、レビュアーが C を確認したうえで作成する。
コミットは CLAUDE.md の恒久ルールにより利用者の明示指示があるまで行わない。

---

## G1a-R6 — 2026-08-26 承認後の成果物保護

**結論**: 指摘 3 件すべて妥当。指摘 1 は承認プロトコルの実効性を無にする穴だった。
実 git の clone で正常系と改変 5 パターンを実地試験した。

### 指摘 1: 署名済み A の後に成果物を改変できた

SR-38 が署名済み commit と比較していたのは **`tests/approvals/g1.yaml` だけ**で、
`coverage.yaml` などの現在値は見ていなかった。
`git log -1 -- tests/approvals/g1.yaml` は改変後も署名済み A を返すため、
**A の後に coverage を書き換えて `obligation_digest` を再計算すれば通った**
（未コミットでも、未署名 commit にしても `50/50 PASS`）。

**修正**: 署名済み A を特定したあと、**保護対象ファイルの現在値を A の tree と
バイト比較**する。

```
tests/coverage.yaml   tests/specs.yaml      tests/predicates.yaml
tests/approvals/g1.yaml   tools/g1_validate.py   tools/g1_extract.py
```

加えて **`tests/` 配下のファイル集合**が A と一致することも確認する（追加・削除の検出）。
**validator 自身を保護対象に含めた**のは、検査器を弱める改変を検出するためである。

### 実地試験（clone した実リポジトリ）

正常系: 対象 commit C → SSH 署名付き承認 commit A → **50/50 PASS / complete=true**

| 改変 | 結果 |
|---|---|
| A の後に coverage の `level` を改変 + digest 再計算（未コミット） | **BLOCK** |
| さらに未署名 commit B にして tree を clean に | **BLOCK**（clean でも検出） |
| `PROTECTED_PATHS` を空にして validator を無効化 | ⚠ **この試験は誤りだった**（G1a-R7 で訂正） |
| `tests/` にファイルを追加 | **BLOCK**（ファイル集合の不一致） |
| `evidence.reviewers` を空に | **BLOCK**（承認記録の改変として検出） |

### 指摘 2・3

- **SR-38**: `evidence.kind` / 非空の `reviewers` / `evidence_url` を**必須化**。
  `reviewers` が空なら reviewer 照合を素通りしていた
- **レポート**: `g1_approval` を新設し、`target_commit` / `approval_commit` /
  **署名者と鍵 fingerprint**（`%GS|%GK|%GT`）/ `artifact_digests` /
  保護対象ファイルの digest / reviewers / 承認義務数を記録

### 限界の明示

`tools/ci-stages.md` に、validator が**保証できること／できないこと**を表で書いた。

| 保証できる | 保証できない |
|---|---|
| 署名鍵の保持者が承認記録に署名した | その鍵が実在のレビュアーのものか（`allowedSignersFile` / CODEOWNERS 依存） |
| 承認後に保護対象が変わっていない | **改変された validator を実行した場合**の結果（自己検査の原理的限界。CI では承認済み commit から checkout した validator を使う） |
| レビュアーが原文を読んだと記録したこと | レビュアーが実際に原文を読んだこと |

累計で塞いだ攻撃は **23 パターン**。

---

## G1a-R7 — 2026-08-26 信頼された実行入口と signed-tag

**結論**: 指摘 2 件とも妥当。加えて **R6 の私の試験報告が誤っていた**ので訂正する。

### 訂正: R6 の「`PROTECTED_PATHS` を空にして BLOCK」は誤り

私が実行した置換は

```python
s.replace('PROTECTED_PATHS=(', 'PROTECTED_PATHS=() or (')
```

で、`() or ('tests/...', ...)` は **非空タプルを返す**（`()` は falsy）。
つまり `PROTECTED_PATHS` は空になっておらず、観測した BLOCK は
**ファイルを編集したこと自体**が保護対象比較に引っかかったものだった。
検査が生き残ったからではない。

レビュアーが正しく空にしたところ `50/50 PASS` になった。
**validator の自己保護は実効的ではない**というご指摘が正しい。

### 指摘 1: 信頼された実行入口を作った

自己検査には原理的限界がある（改変された validator は自分の改変を報告しない）。
`tools/g1_trusted_verify.py` を新設し、**現在の checkout の validator を実行しない**構成にした。

1. 承認記録を最後に変更した commit A を git から特定
2. A の署名を検証（`signed-commit` / `signed-tag`）
3. **A の tree から** validator 一式を隔離ディレクトリへ取り出す
4. `python -I` で実行し、検査対象は `G1_REPO_ROOT` で渡す

**shadow import も塞いだ**。ランナーは冒頭で自分のディレクトリを `sys.path` から外し、
validator は `tools/` を `sys.path` に載せず `g1_extract` を明示パスで `importlib` 読み込みする。
併せて **SR-40**（`tools/` に未追跡 `.py` がない）と
**`tools/` のファイル集合の一致**（commit された shim の検出）を追加した。

### 指摘 2: signed-tag を実際に検証するようにした

`signed-tag` を受理しながら `git verify-commit` しか実行していなかった。
`evidence.tag` を必須にし、**`git verify-tag` + tag が承認 commit を指すこと**を確認する。

### 実地試験（クリーンな clone、SSH 署名）

| # | 状況 | 結果 |
|---|---|---|
| 0 | 正常系（signed-commit） | **51/51 PASS** / exit 0 |
| 1 | `PROTECTED_PATHS` を**本当に**空にした validator | 直接実行では素通り → **trusted runner で BLOCK**（SR-38: coverage と validator の両方が承認時と不一致） |
| 2 | 未追跡 `tools/yaml.py` で shadow import | **shim が実行されず** BLOCK（SR-40 + ファイル集合） |
| 3 | shim を **commit** して clean にする | **BLOCK**（`tools/` のファイル集合が不一致） |
| 4 | tag を作らず `kind: signed-tag` | **exit 2**（`evidence.tag` 必須） |
| 5 | tag が別 commit を指す | **exit 2** |
| 6 | 正しい署名 tag が承認 commit を指す | **51/51 PASS** / exit 0 |

### 訂正: ワーキングツリーの「clean」報告

前回「working tree: 0 件の変更」と報告したが、その後 validator を実行したため
`build/spec-reconcile-report.json` が変更状態になっていた（`run_id` / `executed_at` が毎回変わる）。
承認対象外のファイルなので承認はブロックしないが、**報告としては不正確だった**。

### 限界（変わらず明示する）

| 保証できる | 保証できない |
|---|---|
| 署名鍵の保持者が承認記録に署名した | その鍵が実在のレビュアーのものか |
| 承認後に保護対象ファイルが変わっていない | **ランナー自身**が改変された場合（CI では承認済み commit から取り出したランナーを使う） |
| レビュアーが原文を読んだと記録したこと | レビュアーが実際に読んだこと |

累計で塞いだ攻撃は **29 パターン**。

---

## G1a-R8 — 2026-08-26 検査器の取得元と runner の隔離

**結論**: 指摘 4 件すべて妥当。指摘 1 は「**承認者が検査器を定義できる**」という
承認モデルの根本的な穴だった。

### 指摘 1: 承認 commit A が validator を差し替えられた

trusted runner は validator を **A の tree** から取り出していた。
A の署名者が承認記録と一緒に validator を弱体化すれば、
その弱体化版が「信頼された validator」として実行される。
実際、A に「即座に `51/51 PASS` を出して終了する validator」を含めて署名すると
`exit 0` になった。

**修正**:

- validator の取得元を **`G1_VALIDATOR_COMMIT`（CI が外部から固定）> C（対象 commit）** に変更。
  **A からは取らない**
- **A が C の子孫**であることを要求（`git merge-base --is-ancestor`）
- **`C..A` の変更を `tests/approvals/g1.yaml` だけ**に制限
- その結果 **承認時に `coverage.yaml` を編集しない**設計になった。
  完了状態は承認記録から導出する（`g1.state` は導出値、`g1.authored_state` が記載値）

### 指摘 2: runner 自身が PYTHONPATH で shadow import された

`sys.path[0]` の削除だけでは `PYTHONPATH` 由来のパスが残る。
**runner が隔離モードでなければ自分自身を `python -I` で起動し直す**ようにした
（サードパーティを一切 import する前に実行する）。

### 指摘 3・補足

- コミット済みレポートが `blocking_failures: 1`（コミット前の未追跡ファイル）を
  記録していた。コード commit 後にレポートを生成し直して同じ commit に amend する運用にした
- **signed-tag の署名者情報**を記録するようにした。
  tag object ID / tagged commit / tagger / `git verify-tag --raw` の出力。
  従来は commit の `%GS/%GK/%GT` しか取らず、unsigned commit + signed tag では空だった

### 実地試験（クリーンな clone、SSH 署名）

| # | 状況 | 結果 |
|---|---|---|
| 0 | 正常系（coverage を編集しない） | **51/51 PASS** / exit 0 |
| 1 | A に**即時 PASS する validator** を含めて署名 | **exit 2**（`C..A` の変更が承認記録だけでない） |
| 2 | `PYTHONPATH=.` + 未追跡 `yaml.py` | **shim 実行 0 回** / 51/51 PASS |
| 3 | A に承認記録 + coverage 改変を両方入れて署名 | **exit 2** |
| 4 | 別枝の `C'` を `target_commit` に指定 | **exit 2**（A が C の子孫でない） |

### 限界（更新）

| 保証できる | 保証できない |
|---|---|
| 署名鍵の保持者が承認記録に署名した | その鍵が実在のレビュアーのものか |
| `C..A` が承認記録の追加だけであること | **C 自体**を署名者が作った場合（CI で `G1_VALIDATOR_COMMIT` を外部固定して緩和） |
| 承認後に保護対象ファイルが変わっていない | **runner 自身**が改変された場合（CI は固定した commit / hash から runner を取得すること） |

累計で塞いだ攻撃は **34 パターン**。

---

## G1a-R9 — 2026-08-26 trust anchor の固定とランナーの外部化

**結論**: 指摘 2 件とも妥当。加えて runner の docstring / 実行時メッセージに
旧仕様（「A から validator を取り出す」）が残っていた点も訂正した。

### 指摘 1: `G1_VALIDATOR_COMMIT` が可変 ref を受理していた

存在確認しかしていなかったため `HEAD` / `main` が通り、
署名済み A の後に弱体化した validator を未署名 B として置き
`G1_VALIDATOR_COMMIT=HEAD` で実行すると `51/51 PASS / exit 0` になった。

**修正**: **40 桁完全 SHA のみ**を受理し、`git rev-parse --verify <sha>^{commit}` の
出力との**完全一致**を要求する。`target_commit` 側の判定も同じ関数に統一した。

| 与えた値 | 結果 |
|---|---|
| `HEAD` | **exit 2**（可変 ref は不可） |
| `main` | **exit 2** |
| B の完全 SHA | exit 0 — ただしこれは**設計どおり**。外部固定の trust anchor を
運用者が誤って弱体版に向けた場合であり、監査レポートの `provenance.validator_source` に
どの commit を使ったかが記録される |

### 指摘 2: ランナー自身を外部固定する経路がなかった

`C..A` 制約もランナーの中にあるため、ランナーを書き換えれば制約ごと消える。
さらにランナーの `ROOT` が自身の配置から導出されるため、
固定版を別ディレクトリに取り出しても**その隔離ディレクトリを検査してしまう**状態だった。

**修正**:

- ランナーが **`G1_REPO_ROOT`** を受け付けるようにした（省略時のみ自身の位置から導出）。
  検査対象が git リポジトリでなければ exit 2
- **`tools/g1_ci_verify.sh`** を新設。`G1_TOOLS_COMMIT`（40 桁完全 SHA）から
  ランナー + validator + extract を取り出し、`python -I` で実行し、
  検査対象は `G1_REPO_ROOT` で渡す。未設定・可変 ref はいずれも **exit 2**（fail closed）
- 監査レポートに **`provenance`**（`repo_root` / `validator_source` /
  `validator_source_kind` / `runner_source`）を追加
- `ci-stages.md` に **workflow インライン用のスニペット**を掲載
  （ラッパー自身もリポジトリ内のコピーである以上、最後の一枚は CI 設定で固定する）

### 実地試験（クリーンな clone、SSH 署名）

| # | 状況 | 結果 |
|---|---|---|
| 0 | 正常系 | **51/51 PASS** / exit 0 |
| 1 | 弱体化 validator を B に置き `G1_VALIDATOR_COMMIT=HEAD` | **exit 2** |
| 2 | 同 `=main` | **exit 2** |
| 3 | CI ラッパーで `G1_TOOLS_COMMIT=C` に固定 | **BLOCK**（SR-38: validator が承認時と不一致）/ exit 1 |
| 4 | **ランナー自身**を「即時 PASS」に書き換え | 直接実行では素通り → **ラッパー経由なら BLOCK**（SR-38 / SR-40）/ exit 1 |
| 5 | `G1_TOOLS_COMMIT` 未設定 | **exit 2**（fail closed） |

### 訂正

runner の docstring と実行時メッセージに「A の tree から validator を取り出す」という
旧仕様の記述が残っていた（実装は R8 で C 側に変更済み）。文言を実装に合わせた。

累計で塞いだ攻撃は **39 パターン**。

---

## G1a-R10 — 2026-08-26 CI ラッパーの ambient 環境変数

**結論**: 指摘 1 件は妥当。ラッパーの説明（「runner も validator も同じ固定 SHA から」）と
実装（`${G1_VALIDATOR_COMMIT:-$G1_TOOLS_COMMIT}`）が食い違っており、
環境に残った `G1_VALIDATOR_COMMIT` が固定を上書きしていた。

**修正**:

- `env -u G1_VALIDATOR_COMMIT -u G1_RUNNER_COMMIT` で**ambient 値を落とす**
- validator の取得元は**常に `G1_TOOLS_COMMIT`**
- 別 anchor が必要な場合は **`--validator-commit=<40桁SHA>` を明示**（環境変数からは受け取らない）。
  指定時は警告を出力し、`provenance.validator_source` に記録される

**実地試験**（クリーンな clone、SSH 署名）:

| 状況 | 結果 |
|---|---|
| `G1_TOOLS_COMMIT=C` + ambient `G1_VALIDATOR_COMMIT=B`（弱体化） | **ambient は無視され C が使われる**。`provenance` も両方 C |
| `--validator-commit=B` を明示 | 警告つきで B を使用（意図的な別 anchor は許可） |

**訂正**: runner の実行時メッセージが「承認 commit から取り出した validator」のままだった。
実際には anchor（`G1_VALIDATOR_COMMIT` または C）から取得しているため、
`{anchor の先頭 12 桁} から取り出した validator` に修正した。

累計で塞いだ攻撃は **41 パターン**。

---

## G1a-R11 — 2026-08-26 実装前の計画整備

**結論**: 指摘 4 件すべて妥当。承認プロトコルは固まったので、
実装開始前に**計画側の不整合**と**実 CI の不在**を解消した。

### 指摘 1: 旧承認方式が計画書に残っていた

`coverage.yaml` を編集しない新プロトコルに対し、以下が旧方式のままだった。
そのまま `releaseCheck` を実装すると**永久に通らない**規則になっていた。

| 箇所 | 修正 |
|---|---|
| `docs/05` 規則 6b | 「リリース時は coverage の `reviewer` が非 null」→ **`state` は常に `PENDING_REVIEW`。承認は署名済み `tests/approvals/g1.yaml` が正本** |
| `tools/ci-stages.md` `releaseCheck` | 同上 → **固定 SHA の `g1_ci_verify.sh` を実行し `g1.complete == true` と `provenance.validator_source_kind == "external-pin"` を確認**する規則に |
| `tools/ci-stages.md` 承認手順 | 「`g1_state` を APPROVED に」→ **coverage.yaml は編集しない** |
| `docs/01` G1b | 「`reviewer` / `approved_at` を記入」→ 署名済み承認記録で承認 |
| `docs/README` | `49/50` → `50/51` |

### 指摘 2: CI が計画書だけだった

**`.github/workflows/g1.yml`** を実装した。

| job | trigger | ネットワーク | 内容 |
|---|---|---|---|
| `g1-check` | PR / push | 不要 | `g1_docgen.py --check` + 構造規則 |
| `spec-reconcile` | push / 定期 / 手動 | 必要 | 原文と全 22 仕様を強制再取得して照合 |
| `g1b-approval` | `vars.G1_TOOLS_COMMIT` 設定時 | 必要 | 固定 SHA から runner を取り出して隔離実行し、`g1.complete` と provenance を確認 |

`g1b-approval` は **`tools/g1_ci_verify.sh` を呼ばず、同等の処理を workflow に展開**している。
ラッパー自身も改変されうるため、**CI 設定側に置くことが最後の trust anchor** になる。
併せて **`.github/CODEOWNERS`** で `.github/` と `tools/g1_*` と `tests/` を保護対象にした
（branch protection と併用しないと、workflow を書き換えるだけでゲートが無効になる）。

### 指摘 3: G1b とケース実装の間にゲートがなかった

**設計ゲート G2** を新設した（[01](01-scope-and-roadmap.md)）。

- 132 義務（`NOT_OBSERVABLE` の 1 件を除く）をケース ID に割り当て
- `required_variants` の網羅を `covers_variants` で機械検証
- 各ケースに **positive / negative control** と
  **`counterexample_ja`（義務を満たさないのに PASS する実装）**を必須化
- `depends_on` / `destroys_session` / マイルストーン割当を機械可読に（`tests/cases.yaml`）
- **実現性スパイク S1〜S6**（ECP+SAML-EC / SLO / MDQ variant / secondary_peer /
  生 XML 生成 / 生クエリ文字列）を先に潰す
- **ケース作成者以外**が設計を署名承認する

**M0（骨格）は G1b 後に着手してよいが、M1（判定ケース）は G2 完了後**とした。

### 指摘 4: 検出力のオラクルを mutant peer に変えた

「3 実装で結果に差が出ること」は**撤回**した。差が出ないことは Suite の欠陥を意味しない。

**既知の違反を注入した mutant Test IdP / SP** を用意し、
`must_be_detected_by`（この義務が FAIL になること）と
**`must_not_affect`（この義務は PASS のままであること）**を golden test にする。
後者がないと「何でも FAIL にする Suite」が通ってしまうため必須。
`reject-everything` / `accept-everything` を対照用 mutant として置いた。

**ブラウザ自動化の矛盾も解消**した。`BROWSER` が 56 件あるため Full Profile は無人 CI で回せない。

| 用途 | 範囲 | ブラウザ |
|---|---|---|
| CI（PR / 定期） | `AUTOMATED` 9 義務 + mutant golden test | 不要 |
| リファレンス実装の定期実行 | `AUTOMATED` subset のみ | 不要 |
| Full Profile | 全 132 義務 | 必要。手動実行 + 固定サンプル公開 |

**決定: Phase 1 ではブラウザ自動化を導入しない。**
リファレンス実装は **役割別マトリクス**（IdP/SP）+ **image digest 固定** + **設定 fixture** で
再現性を確保する（`tests/reference-impls.yaml`、M4 までに作成）。

---

## G1a-R12 — 2026-08-26 CI の fail-open と G2 の実体化

**結論**: 指摘 4 件すべて妥当。CI に fail-open が 2 件あった。

### 指摘 1: `g1-check` が validator のクラッシュを古いレポートで隠していた

`g1_validate.py --offline || true` で終了コードを捨てたうえ、
**追跡済みの `build/spec-reconcile-report.json`** を読んでいた。
レポート生成前にクラッシュしても古い正常結果で job が成功する。

**修正**: validator に **`--structural-only`** モードを追加した。
原文を一切参照せず構造規則だけを実行し、**自分の終了コードを返す**。
CI 側のハードコード除外リストは撤去し、実行前に `rm -f` でレポートを消し、
実行後に `mode == "structural-only"` のレポートが生成されたことを確認する。

### 指摘 2: 署名検証より前に未保護のコードを実行していた

`g1b-approval` が現在ブランチの `tools/requirements.txt` を `pip install` していた。
このファイルは CODEOWNERS の外で、PR で任意パッケージ・URL・ローカルビルドを
追加すれば**署名検証より前に任意コードが走る**。

**修正**:

- **`tools/requirements.lock`** を生成（推移依存 6 件・375 hash）。
  `pip install --require-hashes` で導入する
- `g1b-approval` は **`git show $G1_TOOLS_COMMIT:tools/requirements.lock`** で
  依存も固定 SHA から取り出す。現在ブランチのファイルは使わない
- CODEOWNERS に `tools/requirements.txt` / `tools/requirements.lock` を追加
- Actions を **完全 commit SHA で固定**（`actions/checkout@11bd719…` など）
- `if: vars.G1_TOOLS_COMMIT != ''` は required check として fail-open なので廃止。
  **`vars.G1B_ENABLED == 'true'` なら常に実行し、`G1_TOOLS_COMMIT` 未設定なら失敗**する
- provenance の `validator_source` / `runner_source` が **pin と一致すること**まで確認する

### 指摘 3: mutant の義務カバレッジが G2 の通過条件になかった

10 義務しか覆わない mutant セットでも「全 mutant の期待結果が一致した」で通せた。

**修正**: G2 の通過条件に
**「各義務が実行可能な mutant で検出される、または `mutant_waiver`
（理由 + 代替の実行可能な control fixture）を持つ」**を追加。
併せて `covers_variants` を**配列インデックスから安定 ID 参照**に変え、
`coverage.yaml` の `required_variants` にも `id` を持たせる形にした
（並び替えで対応が壊れないように）。
ケースのマイルストーン割当も **M1〜M3**（M0 はテスト 0 件なので除外）に訂正した。

### 指摘 4: mutant のオラクルに成立しない条件があった

- 「正常 peer では全義務 PASS」→ 役割違い・条件付き・CONFIG・ATTESTED があるため**成立しない**
- 「`reject-everything` ではどの義務も PASS してはならない」→ **誤り**。
  一律拒否でも `MUST_NOT` 系は満たせる
- `must_not_affect` を常に PASS とすると、対象外・条件偽・前提不足を表現できない

**修正**: **baseline outcome vector** 方式に変えた。
役割・Test Profile・条件を固定した `baseline.yaml` に全 133 義務の期待 verdict を置き、
mutant は `expected_changes`（baseline から変わるべき義務）と
`unchanged_required: all_others`（それ以外は baseline と一致）で判定する。
`reject-everything` / `accept-everything` は
**control の機能そのものを検証する対照 mutant** と位置づけ直した。

### G2 の検証基盤を明示

「G1b と同じ方式で署名承認」だけでは実体がないので、
Codex に渡す単位として列挙した — `schema/cases-v1.json` / `tests/cases.yaml` /
`tests/mutants/*.yaml` / `tools/g2_validate.py` / `tests/approvals/g2.yaml` /
`case_digest` / `mutant_digest` / `g2.complete` / `.github/workflows/g2.yml` /
作成者とレビュアーの分離規則（G1 と同じ）。

### `AGENTS.md` を追加

Codex の実装逸脱を抑えるため、リポジトリ直下に置いた。9 つの絶対規則
（承認済み成果物を編集しない / 生成物を手編集しない / ケースは Verdict を返さない /
送信は outbox のみ / `NOT_APPLICABLE` の限定 / 原文にない閾値を足さない /
対照のないケースを作らない / 生リクエストを壊さない / 資格情報を永続化しない）と、
変更ごとに実行する検証コマンド、ゲートの順序を短くまとめた。
「過去に何を間違えたか」として `docs/11-review-log.md` を読ませる導線も入れた。

---

## G1a-R13 — 2026-08-26 G1b 開始前の最終整備

**結論**: 指摘 7 件すべて妥当。特に 2 件は**私の計画が自分の規約と矛盾**していた。

### 指摘 1: G1b がまだ fail-open だった

`if: vars.G1B_ENABLED == 'true'` を置いていたが、
**条件で skip されたジョブは GitHub では Success 扱い**になり、
required check にしてもマージを阻止しない。**変数を消すだけでゲートが無効化**できた。

**修正**: ジョブ条件を撤去し**常に実行**する。承認が済んでいなければ**失敗する**。
G1b 前はこのジョブが赤いのが正しい状態であり、
**required check にするかは branch protection 側で切り替える**（ライフサイクル切替をジョブ条件に置かない）。

### 指摘 2: G2 で承認済みの G1 成果物を変更する計画になっていた

`required_variants` の ID 化を **G2 で行う**と書いていた。
これは G1b 承認後に `coverage.yaml` と全 `obligation_digest` を変更し、**承認を失効させる**。
`AGENTS.md` の「承認済み G1 成果物を編集しない」とも矛盾していた。

**修正**: **G1b の前に**移行を完了した。

- 248 variant すべてを `{id, description_ja}` に変換
- ID は **義務キー + 説明文の内容ハッシュ**（`v-` + 10 hex）。
  並び替えでは変わらず、説明文を編集すれば変わる
- 同一説明文の variant が 8 組あったため、義務キーを混ぜて一意化した
- `g1_docgen.py` と **SR-22b / SR-22c**（形式・一意性）を追従させた

### 指摘 3: mutant baseline が単一 scenario では足りない

`role: sp` の baseline では `IIP-IDP*` が全て `NOT_APPLICABLE` になり、
**IdP の mutant を検出できない**。

**修正**:

- **baseline matrix** に変更（`sp-full-slo-enc` / `sp-core-minimal` / `idp-full` / `idp-core-no-ecp`）。
  各 baseline は role・profile・`declared_features`・**設定 fixture** を固定する
- 各 mutant が **`base`** を明示する
- **「mutant Test Peer」→「mutant target（SUT）」**に用語を訂正。
  Suite 側の `peer/` は常に正しく動く。混同すると「Suite を壊して検出力を測る」誤りになる
- mutant の期待値を **`outcome`** で書く（`violated` 等）。`FAIL` と書くと
  SHOULD 義務を一律 FAIL にする誤りが再発する
- **control の失敗は対象の違反ではない**。`control_failed` として扱い、
  当該ケースは `NOT_VERIFIED(control_failed)` にする

### 指摘 4: 署名者と reviewer が結び付いていなかった

署名者 principal を取得していたが**レポートに出すだけ**で、承認判定は
YAML 内の自己申告 `reviewer` を見ていた。
**許可鍵の保持者が架空の reviewer 名を書けば `reviewer != authored_by` を通せた。**

**修正**: 署名者 principal（commit は `%GS`、tag は tagger）を抽出し、
**全 `reviewer` が署名者 principal（または外部固定の `G1_SIGNER_MAP` で写像した値）と
一致すること**を要求する。複数レビュアーを認める場合は
reviewer ごとの署名済み記録が必要である旨をエラーに明記した。

実地試験（clone した実リポジトリ、SSH 署名）:

| 状況 | 結果 |
|---|---|
| 許可鍵の保持者が `reviewer: fabricated-reviewer` と記録 | **BLOCK**（署名者 `reviewer@example.com` と不一致） |
| `reviewer` を署名者 principal と一致させる | **53/53 PASS** / exit 0 |

### P2 3 件

- **script injection**: `${{ vars.G1_ALLOWED_SIGNERS }}` を run に直接展開していた。
  `env:` 経由に変え、shell 側で `"$G1_ALLOWED_SIGNERS"` を引用参照する
- **コミット済みレポートが古い**: commit 前の未追跡ファイル状態（`blocking 1 / SR-40`）が
  残っていた。clean 状態のレポートに更新する
- **ci-stages.md の旧記述**: offline 除外方式と旧 G1b トリガーを現行に合わせた

累計で塞いだ攻撃は **45 パターン**。
