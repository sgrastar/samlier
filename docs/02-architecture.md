# 02. アーキテクチャ

## 1. 全体構成

```
┌───────────────────────────────────────────────────────────────┐
│ 利用者のブラウザ                                               │
│   (a) Suite の Web UI を開いている                             │
│   (b) 同時に SAML のユーザーエージェントでもある ★             │
└──────┬─────────────────────────────┬──────────────────────────┘
       │ REST + SSE                  │ SAML front-channel
       │                             │ (HTTP-Redirect / HTTP-POST)
       ▼                             ▼
┌───────────────────────────────────────────────────────────────┐
│ samlier  (単一 JVM / 単一コンテナ)                            │
│                                                               │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Web API     │  │ Test Runner  │  │ Protocol Endpoints   │  │
│  │ + UI 配信   │──│ (状態機械)   │──│  /p/{plan}/sp/acs    │  │
│  │             │  │              │  │  /p/{plan}/sp/slo    │  │
│  └─────────────┘  └──────┬───────┘  │  /p/{plan}/idp/sso   │  │
│                          │          │  /p/{plan}/idp/slo   │  │
│  ┌───────────────────────┴───────┐  │  /p/{plan}/metadata  │  │
│  │ SAML Engine                   │  │  /mdq/{entityID}     │  │
│  │  ├ OpenSAML 5  (正常系生成/解析) │  └──────────────────────┘  │
│  │  ├ Santuario   (XML署名/暗号)  │                           │
│  │  └ Raw DOM/StAX (異常系生成) ★ │  ┌──────────────────────┐  │
│  └───────────────────────────────┘  │ Transcript Recorder  │  │
│                                     │ 全 HTTP + 全 SAML    │  │
│  ┌─────────────┐  ┌──────────────┐  │ メッセージを記録     │  │
│  │ Test Defs   │  │ Key Store    │  └──────────────────────┘  │
│  │ (YAML, 埋込)│  │ (plan 毎鍵)  │  ┌──────────────────────┐  │
│  └─────────────┘  └──────────────┘  │ Store (SQLite)       │  │
│                                     └──────────────────────┘  │
└──────┬────────────────────────────────────────────────────────┘
       │ back-channel (Suite → Target)
       │  - Target メタデータ / MDQ の取得
       │  - SOAP SLO, ECP
       ▼
   Target IdP / Target SP
```

★ が付いた 2 点が SAML 特有で、設計上最も影響が大きい。

- **ブラウザが試験経路の一部**である。Suite は対象に直接ログインできない（利用者の資格情報を預かるべきではない）。
- **異常系 XML を作るには OpenSAML では足りない**。Phase 4 を見据えて、最初から低レベル XML 生成経路を分離しておく。

## 2. 技術スタック

| 層 | 選択 | 状態 |
|---|---|---|
| 言語 / ランタイム | Java 21 (LTS) | 確定 |
| SAML | OpenSAML 5.x（Java 17+ / Apache-2.0） | 確定 |
| XML Security | Apache Santuario XML Security for Java | 確定 |
| 低レベル XML | JDK 標準 DOM / StAX + 文字列テンプレート | 確定 |
| Web フレームワーク | **Javalin + Jetty** | 確定（生クエリ文字列へのアクセスが必須。§3.5） |
| DB | SQLite (xerial sqlite-jdbc)、アクセス層を薄く保つ | 暫定確定 |
| フロントエンド | **React + Vite (TypeScript)** | 確定 |
| 配布 | Docker（マルチアーキ: amd64 / arm64） | 確定 |
| ビルド | **Gradle (Kotlin DSL)** | 確定 |

### OpenSAML に依存しすぎない方針

```
                 ┌──────────────────────────┐
   正常系  ──────│ MessageFactory (OpenSAML)│──┐
                 └──────────────────────────┘  │   ┌──────────────┐
                                               ├──▶│ Serializer   │──▶ wire
                 ┌──────────────────────────┐  │   │ (DOM → bytes)│
   異常系  ──────│ RawMessageBuilder        │──┘   └──────────────┘
   (Phase 4)     │ (DOM 直接操作 / 文字列)   │
                 └──────────────────────────┘
```

- 生成の最終段は**必ず DOM または生バイト列**に落とす。OpenSAML のオブジェクトモデルを最終形にしない
- 受信側も同様: OpenSAML でのパース結果と、生 XML の DOM の**両方**を保持する。
  （OpenSAML が正規化・無視した情報が判定に必要になることがある。コメント切り詰め攻撃など）
- 署名は Santuario を直接叩けるようにしておく（OpenSAML の Signer 経由だと不正署名が作れない）

> Phase 1 の時点で異常系ビルダーは使わないが、**インターフェースだけは Phase 1 で切る**。
> あとから差し込むと生成経路が二重化して破綻する。

## 3. Test Peer 設計 ★ 本設計の核心

### 問題: SAML には動的クライアント登録がない

OIDC Conformance Suite はテストごとに新しい issuer / client を発行できる。
SAML では **対象側に手作業でメタデータを登録してもらう**必要がある。
テストケースごとに entityID を変えると、利用者は 50〜80 回の登録作業を強いられ、誰も使わない。

### 解決: 1 Test Plan = 1 entityID = 1 つの「全部入りメタデータ」

Test Plan を作ると、Suite は次を 1 セット発行する。

```
entityID : https://<base>/p/{planId}
metadata : https://<base>/p/{planId}/metadata      (署名付き)
MDQ      : https://<base>/mdq/{urlencoded-entityID}
```

このメタデータには、その Test Plan に含まれる**全テストケースが必要とする全ての要素**を最初から入れておく。

#### Test SP としてのメタデータ（IdP をテストする場合）

```xml
<SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" ...>
  <!-- 複数署名鍵: IIP-MD07 / MD11 のテストに使う -->
  <KeyDescriptor use="signing">     <!-- 鍵 A: 既定 -->
  <KeyDescriptor use="signing">     <!-- 鍵 B: ロールオーバー先 -->
  <KeyDescriptor use="encryption">  <!-- 鍵 C -->
  <KeyDescriptor use="encryption">  <!-- 鍵 D: 復号ロールオーバー -->
  <KeyDescriptor>                   <!-- 鍵 E: use 属性なし → IIP-MD11 -->

  <!-- アルゴリズム宣言: IIP-MD09 / MD10 のテストに使う -->
  <alg:DigestMethod Algorithm="...sha256"/>
  <alg:SigningMethod Algorithm="...rsa-sha256"/>

  <SingleLogoutService Binding="HTTP-Redirect" .../>
  <SingleLogoutService Binding="HTTP-POST" .../>
  <SingleLogoutService Binding="SOAP" .../>

  <!-- ACS を index で複数持ち、ケース切替に使う -->
  <AssertionConsumerService index="0" Binding="HTTP-POST"     isDefault="true"/>
  <AssertionConsumerService index="1" Binding="HTTP-Artifact"/>   <!-- Phase 2 -->
  <AssertionConsumerService index="2" Binding="PAOS"/>            <!-- ECP -->
  <AssertionConsumerService index="3" Binding="HTTP-POST"/>       <!-- 予備 -->

  <!-- IIP-IDP04 の検証に使う -->
  <AttributeConsumingService index="0">
    <RequestedAttribute .../>
  </AttributeConsumingService>
</SPSSODescriptor>
```

#### Test IdP としてのメタデータ（SP をテストする場合）

```xml
<IDPSSODescriptor WantAuthnRequestsSigned="false" ...>
  <KeyDescriptor use="signing"> × 2      <!-- 鍵ロールオーバーテスト -->
  <KeyDescriptor use="encryption"> × 1
  <SingleSignOnService Binding="HTTP-Redirect" .../>
  <SingleSignOnService Binding="HTTP-POST" .../>
  <SingleLogoutService Binding="HTTP-Redirect|HTTP-POST|SOAP" .../>
  <NameIDFormat>persistent</NameIDFormat>
  <NameIDFormat>transient</NameIDFormat>
</IDPSSODescriptor>
```

**メタデータに載せない鍵**も Test Plan は保持する（未登録鍵で署名して拒否されるかを見る Phase 4 用）。

### ケース切替のメカニズム

| 方向 | 誰が始めるか | ケースの特定方法 |
|---|---|---|
| **IdP テスト** | Suite（Test SP） | Suite が AuthnRequest を作るので自由。`RelayState` にケース ID を入れ、`InResponseTo` で照合。ACS index / Binding も自由に選べる |
| **SP テスト・レスポンス処理系** | Suite（Test IdP、unsolicited） | Suite が Response を生成し、ブラウザ経由で対象 ACS に POST。ケース ID は Suite 側の状態で保持 |
| **SP テスト・リクエスト生成系** | 対象 SP | **アーミング方式**。UI で「次に受け取る AuthnRequest をケース N として扱う」と宣言してから、利用者が SP でログインを開始する |

> **アーミング方式が必要な理由**: SP が発行する AuthnRequest の宛先は、SP が Test IdP の
> メタデータから選んだ `SingleSignOnService` の Location である。ケースごとに URL を変えることはできない。
> したがって「今どのケースを試験中か」は Suite 側の状態で持つしかない。
> 同時に複数ケースをアームすることはできない → **SP のリクエスト生成系テストは逐次実行**になる。
>
> 一方、レスポンス処理系（SP が不正な Assertion を拒否するか等）は Suite 起点なので
> **並列化・自動化が可能**。SP プロファイルのテストの大半はこちらに寄せる。
> ただし unsolicited（IdP-initiated）SSO を無効にしている SP もあるため、
> Test Plan に `sp_accepts_unsolicited: yes/no` を持ち、no の場合はアーミング方式にフォールバックする。

### セッションの扱い

- IdP テストでは利用者が対象 IdP にログインする必要がある。**初回だけログインし、以降は IdP 側の SSO セッションで自動的に通す**
- `ForceAuthn`（IIP-IDP06）のテストだけは再認証が必要になるため、テスト順序で末尾側に寄せる
- `IsPassive`（IIP-IDP07）はセッションの有無で期待結果が変わるため、直前に「ログイン済みであること」を前提とする

## 3.5. 生リクエストへのアクセスという必須要件

Web フレームワーク選定を縛る技術的制約なので明記しておく。

**HTTP-Redirect バインディングの署名は、URL デコード前のクエリ文字列そのものを対象にする。**

```
SAMLRequest=fZJNT%2BMwEIb%2F...&RelayState=abc&SigAlg=http%3A%2F%2F...
└──────────────── この生バイト列が署名対象 ────────────────┘
```

パラメータをパースして再構成すると、パーセントエンコーディングの差異
（`%2F` と `/`、`+` と `%20`、大文字小文字）で署名検証が壊れる。
同様に HTTP-POST バインディングでも、base64 文字列を再エンコードしてはいけない。

したがって Suite は次を満たす必要がある。

- 受信時に**生のクエリ文字列**（`getQueryString()` 相当）と**生のボディバイト列**を取得できること
- フレームワークやフィルタが URL を正規化・再エンコードしないこと
- リバースプロキシを挟む場合、プロキシがクエリ文字列を書き換えない設定であること（README に記載）
- Transcript には**デコード前の生の値とデコード後の値の両方**を残すこと

> ここを取り違えると「対象の署名が不正」と誤判定するテストスイートになる。
> 実装の最初期に、生バイト列を保持する経路をテストで固定する。

## 3.7. ★ ECP（Enhanced Client or Proxy）の役割配置

IIP-IDP13〜16 は **IdP** に課される MUST 義務なので、Phase 1 で試験するのは
**対象 IdP の ECP 対応**である。このとき Samlier が演じるのは
**ECP クライアント + SP** であり、**Test IdP ではない**。

ECP は「ECP クライアント ↔ SP」「ECP クライアント ↔ IdP」の 2 区間からなる
（[OASIS ECP Profile v2.0](https://docs.oasis-open.org/security/saml/Post2.0/saml-ecp/v2.0/saml-ecp-v2.0.html)）。
Samlier は SP 役も兼ねるため、SP との区間は内部で完結できる。

```
┌───────────────────────────────────────────────────────────┐
│ Samlier                                                   │
│                                                           │
│  ┌────────────┐  ① AuthnRequest を自分で生成（SP 役）      │
│  │ Test SP    │─────────────┐                             │
│  │ (peer/sp)  │             ▼                             │
│  └────────────┘   ┌──────────────────┐                    │
│         ▲         │ ECP Client       │                    │
│         │         │ (peer/ecp)       │                    │
│         │         └────────┬─────────┘                    │
│         │                  │ ② SP 由来の SOAP ヘッダを     │
│         │                  │    **全て除去**して            │
│         │                  │    AuthnRequest を SOAP 送信   │
│         │                  │    + HTTP Basic 認証           │
│         │                  │    (+ ECP 自身の cb:ChannelBindings) │
│         │                  ▼                               │
│         │        ┌────────────────────────┐                │
│         │        │  Target IdP            │                │
│         │        │  SOAP SSO endpoint     │                │
│         │        └───────────┬────────────┘                │
│         │                    │ ③ SOAP Response             │
│         │                    │   (ecp:Response, Assertion) │
│         │                    ▼                             │
│  ┌──────┴───────────────────────────────┐                  │
│  │ ④ POST /p/{plan}/sp/paos             │  ⑤ 検証・判定    │
│  │    PAOS Response Consumer            │                  │
│  └──────────────────────────────────────┘                  │
└───────────────────────────────────────────────────────────┘
```

### ★ 区間ごとにヘッダ集合が違う（ECP v2 §2.3.4）

> *Any header blocks received from the service provider **MUST be removed**.*

ECP は SP から受け取った SOAP ヘッダブロックを、IdP に転送する前に**除去する**。
PAOS は主に **ECP ↔ SP** 区間のものであり、**IdP に PAOS ヘッダを送るのは誤り**。

| 区間 | SOAP ヘッダ |
|---|---|
| SP → ECP | `paos:Request`, `ecp:Request`, `ecp:RelayState`, （SP が付ける）`cb:ChannelBindings` |
| **ECP → IdP** | **上記は全て除去**。ECP 自身が付ける `cb:ChannelBindings`（client↔SP チャネルを表す）のみ |
| IdP → ECP | `ecp:Response`, （一致した）`cb:ChannelBindings`, `samlec:*` |
| ECP → SP | `paos:Response`, `ecp:RelayState`（SP から受け取ったものを戻す） |

Samlier は SP 役も兼ねるため ① は内部で完結するが、
**②の組み立て時に①のヘッダを引き継がないこと**を実装で強制する
（`EcpClient` が SP 由来ヘッダを保持しないデータ構造にする）。

設計上の含意:

- Test SP のメタデータに **`<AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:PAOS" index="2">`**
  を必ず含める（IIP-IDP16「ECP 設定をメタデータから取り込む」の検証に必要）
- `ecp:Response/@AssertionConsumerServiceURL` が **メタデータの PAOS ACS と一致するか**を検証する。
  一致しない URL を返す IdP は IIP-IDP16 違反であり、Open Redirect の観点でも重要
- **ブラウザを一切使わない**ため `AUTOMATED` として完全自動化できる。
  IIP の中で最も自動化しやすい領域
- **Test IdP 側の ECP エンドポイントは Phase 1 では不要**。
  IIP に SP 向けの ECP 義務はないため、SP の ECP 対応を試験するのは Phase 2 以降

### ★ ECP と SAML-EC は別の仕様であり、別のケースが要る

IIP-IDP13〜16 が参照するのは **[SAML2ECP] ECP Profile v2.0** だが、
**IIP-IDP15 だけは別文書を参照している**。

> *Identity Providers MUST support the generation and inclusion of a random key
> in accordance with **[SAML-EC], Section 5.3.1**.*

`[SAML-EC]` は IETF kitten WG の
[SAML Enhanced Client SASL and GSS-API Mechanisms](https://datatracker.ietf.org/doc/html/draft-ietf-kitten-sasl-saml-ec-16)
（インターネットドラフト）であり、ECP Profile ではない。

| 義務 | 参照仕様 | 検査対象 | 通常の ECP 往復で検証できるか |
|---|---|---|---|
| IIP-IDP13.a | ECP Profile v2.0 | `SubjectConfirmation/@Method` = Bearer、`@Recipient` | ✅ |
| **IIP-IDP13.b** | ECP Profile v2.0 §2.3 | **channel bindings の検証** | ✅（下記のケース群が必要） |
| IIP-IDP14 | RFC 2617 | HTTP Basic 認証 | ✅ |
| **IIP-IDP15** | **[SAML-EC] §5.3.1** | **`<samlec:GeneratedKey>`**（Assertion の `<saml:Advice>` 内） | ❌ **別ケースが必要** |
| IIP-IDP16 | ECP Profile v2.0 §2.3.10 | メタデータからの設定取り込み | ✅ |

したがって `peer/ecp/` を 2 系統に分ける。

```
peer/ecp/
  ├─ profile/   ECP Profile v2.0 クライアント（IDP13, IDP14, IDP16）
  └─ samlec/    SAML-EC 拡張クライアント（IDP15）
                 SAML-EC 用の要求を送り、Advice 内の samlec:GeneratedKey を検査する
```

**参照ドラフトの版を `specs.yaml` に固定する**（[05 §5](05-test-definition-format.md) の規則 28）。
ドラフトは版によって章番号も要素定義も変わりうるため、
「§5.3.1」がどの版のものかを結果に残せないと再現性がない。

### ★ channel bindings（IIP-IDP13.b）のテストケース群

原文は *MUST support "Bearer" subject confirmation **and verification of channel bindings*** であり、
channel bindings の**検証**まで MUST に含まれる。
ECP v2 §2.3.6.2 は、一致した場合の**出力**まで規定している。

> *…MUST include at least one `<cb:ChannelBindings>` element … as **SOAP header blocks** in its message to the client.*
> *…MUST include at least one `<cb:ChannelBindings>` element in the **`<saml:Advice>`** element of any `<saml:Assertion>` elements that it returns.*
> *The `<samlp:AuthnRequest>` message **MUST be signed** if the channel bindings extension option is used.*

| # | 入力 | 期待 |
|---|---|---|
| 1 | SP と ECP クライアントの channel binding が**一致**。`AuthnRequest` は署名済み | 認証成功に加えて、**`cb:ChannelBindings` が (a) 応答の SOAP ヘッダブロック と (b) 返却された Assertion の `<saml:Advice>` の両方に含まれる**こと。★どちらか一方しかなければ IIP-IDP13.b 違反 |
| 2 | **不一致** | エラー `<samlp:Response>` が返る。Assertion を返してはならない |
| 3 | `AuthnRequest` の `<Extensions>` にのみ channel binding が存在 | ECP v2 の規定に沿った扱い。少なくとも**成功した Assertion を無検証で返さない**こと |
| 4 | SOAP ヘッダ側にのみ存在 | 同上 |
| 5 | channel binding を使用しているが **`AuthnRequest` が未署名** | ★ 期待を具体化: 仕様が署名を MUST としているため、**エラー Response が返ること**。署名なしで Assertion を発行したら FAIL |

ケース 2・5 は negative test なので [03 §5](03-test-model.md) の証拠ラダーに従う。
ECP はバックチャネルなので L1（SAML Status エラー）で自動判定できる可能性が高い。

## 4. エンドポイント設計

```
GET  /                              Web UI
GET  /api/plans                     Test Plan 一覧
POST /api/plans                     Test Plan 作成
GET  /api/plans/{id}                Test Plan 詳細（+ 発行された entityID / metadata URL）
POST /api/plans/{id}/runs           Test Run 開始
GET  /api/runs/{id}                 Run の状態（SSE でストリーム）
POST /api/runs/{id}/cases/{cid}/arm     ケースをアーム（SP テスト）
POST /api/runs/{id}/cases/{cid}/attest  利用者の観測結果を申告
GET  /api/runs/{id}/transcript      通信ログ
GET  /api/runs/{id}/result.json     結果 JSON
POST /api/runs/{id}/publish         結果公開（opt-in）

--- SAML プロトコル面 ---
GET  /p/{plan}/metadata             Test Peer のメタデータ（署名付き）
GET  /p/{plan}/metadata?variant=X   異常系メタデータ（expired / unsigned / badsig / no-validUntil）
GET  /mdq/{encodedEntityID}         Metadata Query Protocol
POST /p/{plan}/sp/acs/{index}       Test SP: Assertion Consumer Service
GET|POST /p/{plan}/sp/slo           Test SP: Single Logout
POST /p/{plan}/sp/slo/soap          Test SP: SOAP SLO
POST /p/{plan}/sp/paos              Test SP: ECP (PAOS) Response Consumer ★
GET|POST /p/{plan}/idp/sso          Test IdP: SSO
GET|POST /p/{plan}/idp/slo          Test IdP: SLO
POST /p/{plan}/idp/slo/soap         Test IdP: SOAP SLO
GET  /p/{plan}/start/{caseId}       ブラウザ操作の起点（利用者がクリックする）
```

> `?variant=` によるメタデータの差し替えは IIP-MD03 / MD04 の検証に必須。
> ただし **variant を切り替えた瞬間に対象がキャッシュを更新するとは限らない**ため、
> variant は「Test Plan の現在の配布状態」として Suite 側の状態で管理し、
> 利用者に「対象のメタデータを再読込してください」と指示する対話ステップを挟む。

## 5. Transcript Recorder

判定の根拠を全て残す。これが Suite の価値の半分を占める。

### 5.1 記録するもの

```
- 方向 (inbound / outbound), timestamp (ms), 相関ID
- HTTP: method, URL, status, ヘッダ（§5.2 の除去後）, 生ボディ（§5.2 の除去後）
- SAML: 
    - エンコード前の生バイト列（Redirect の deflate+base64、POST の base64 を解いたもの）
    - 整形済み XML
    - OpenSAML でのパース結果サマリ（Issuer, ID, InResponseTo, Destination, Conditions, Status ...）
    - 署名検証の詳細（参照 URI、Transform 一覧、使用鍵、検証結果）
- 判定に使った条件式とその評価結果
```

UI ではテスト結果 → 判定理由 → 該当トランザクション → 生 XML までワンクリックで辿れること。

### 5.2 ★ 秘匿情報の除去は Recorder への投入前に行う

**公開時のスクラブでは遅い。** ECP テスト（IIP-IDP14）では HTTP Basic 認証を使うため、
`Authorization` ヘッダをそのまま記録すると **可逆な Base64 のまま資格情報が
`/data` に永続化される**。公開しなくても、ディスク・バックアップ・
Transcript ダウンロードから漏れる。

Recorder は入口に **Redactor** を持ち、永続化する前に不可逆に落とす。

| 対象 | 処理 |
|---|---|
| `Authorization` / `Proxy-Authorization` | 値を捨て、`Authorization: <redacted: Basic, 42 bytes>` に置換 |
| `Cookie` / `Set-Cookie` | 名前は残し、値を `<redacted: 24 bytes>` に置換 |
| `application/x-www-form-urlencoded` のボディ | キー名が `password` / `passwd` / `pwd` / `secret` / `token` / `otp` / `pin` に一致する値を除去 |
| Test Plan の `test_user_hint` | Transcript に載せない |
| ECP の資格情報 | **実行中のみメモリ保持**。`CaseState` にも書かない（[05 §4.2](05-test-definition-format.md)） |
| SAML の `<saml:AttributeValue>` | **Transcript には残す**（判定に必要）。公開時にマスクする（[06 §4](06-results-and-publication.md)） |

設計上の要点:

- Redactor は **`Transcript.record()` の内部**にあり、迂回できる API を作らない
- 除去は**不可逆**。「あとで復号できる形」で保存しない
- 除去したこと自体は残す（ヘッダ名・バイト長）。デバッグ時に「存在したか」は分かる
- ケース実装が `ctx.fetch()` 以外で HTTP を叩けない設計（[05 §4.3](05-test-definition-format.md)）が
  この保証を成立させている
- `RedactorTest` で、Basic 認証つきの ECP 往復を実行したあと
  **`/data` 配下の全バイト列に資格情報が現れないこと**を検証する

## 6. コード構成（案）

```
samlier/
├── core/            ドメインモデル (Plan, Run, Case, Result, Verdict)
├── saml/
│   ├── normal/      OpenSAML ベースの生成・解析
│   ├── raw/         DOM/StAX ベースの生成（Phase 4 の足場）
│   ├── crypto/      Santuario ラッパ、鍵生成、アルゴリズム定義
│   └── metadata/    メタデータ生成・variant・MDQ
├── peer/
│   ├── sp/          Test SP のエンドポイントと状態（PAOS ACS を含む）
│   ├── idp/         Test IdP のエンドポイントと状態
│   └── ecp/         ECP クライアント（対象 IdP の ECP 対応を試験する。§3.7）
├── runner/          状態機械、アーミング、attestation
├── tests/
│   ├── defs/        *.yaml（テスト定義、リソースとして埋め込み）
│   └── impl/        Java 実装。YAML の id と 1:1 で紐づく
├── store/           SQLite アクセス
├── api/             REST + SSE (Javalin)
├── auth/            Hosted 版の管理アクセス（シークレット URL、将来の OIDC RP）
│                    ★ peer/ とはセッション・Cookie・オリジンを完全に分離する
└── web/             React + Vite (TypeScript)。report.html も同じアプリから静的ビルド
```

CI で「YAML に対応する実装クラスがある」「実装クラスに対応する YAML がある」を検証する。
