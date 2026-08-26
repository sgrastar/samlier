# 05. テスト定義フォーマット

## 1. 方針

元メモの意図は正しい。ただし表現が誤解を生むので明確にする。

> **YAML は「何を・どの仕様を根拠に・なぜテストするのか」について規範的。
> Java は「どうテストするのか」について規範的。**
>
> さらに **RFC2119 レベルは要件カタログ（`coverage.yaml`）だけが持つ**。
> テスト定義にも実装にも書かない。

YAML に期待結果のロジックを書こうとしてはいけない。
元メモの例:

```yaml
expected:
  expired_metadata: reject     # ← これは実装に踏み込みすぎ / 表現力が足りない
```

これは「reject とは何か」を YAML では定義できないため、結局 Java 側の解釈に依存する。
YAML には**人間が読んで意味が分かる記述**と**機械が使う分類・依存関係**だけを置き、
判定ロジックは実装クラスに委ねる。両者の対応は CI で強制する。

## 2. 二層構造: 要件カタログ と テスト定義

判定レベルは**要件カタログ側**に、テスト手順は**テスト定義側**に置く。
テスト定義に `level: MUST` を書くと、同じ要件の別ケースと矛盾しうる（[03 §1](03-test-model.md)）。

```
tests/coverage.yaml     要件 → 義務（役割・条件・RFC2119 レベル）  ← 判定レベルの唯一の出典
tests/defs/*.yaml       テストケース → どの義務を検証するか        ← 手順と期待
tests/specs.yaml        仕様カタログ（文書名・版・URL）
```

### 2.1 要件カタログ `tests/coverage.yaml`

```yaml
spec: kantara-fedinterop-impl
version: "1.1"

requirements:
  - id: IIP-SP13
    section: "3.1.1"
    anchor: IIP-SP13
    obligations:
      - key: IIP-SP13.a
        roles: [sp]
        level: MUST
        condition: null
        summary_en: "Support the ability to reject unsigned <samlp:Response> elements"
        summary_ja: "未署名の <samlp:Response> を拒否できること"
        testability: BROWSER
        level_assignment: { sp: core }
      - key: IIP-SP13.b
        roles: [sp]
        level: SHOULD                      # ★ 既定で拒否するのは SHOULD
        condition: null
        summary_en: "Reject unsigned <samlp:Response> elements by default"
        summary_ja: "既定設定で未署名 Response を拒否すること"
        testability: BROWSER
        level_assignment: { sp: core }

  - id: IIP-MD01
    obligations:
      - key: IIP-MD01.a
        roles: [idp]
        level: MUST                        # ★ 役割でレベルが違う
        summary_en: "Support metadata acquisition via the Metadata Query Protocol"
        testability: CONFIG
      - key: IIP-MD01.b
        roles: [sp]
        level: SHOULD
        summary_en: "Support metadata acquisition via the Metadata Query Protocol"
        testability: CONFIG
      - key: IIP-MD01.c                    # ★ 文の後半に隠れていた条件付き MUST
        roles: [idp, sp]
        level: MUST
        condition:
          predicate: claims_mdq_support
          predicate_kind: CLAIM_BASED      # 原文が "claim support for this protocol"
          declared: declared_features.mdq
          observed: []
        summary_en: >
          Implementations that claim support for MDQ must be able to request and
          utilize metadata from one or more MDQ responders for any peer from which
          they receive a SAML message
        testability: CONFIG

  - id: IIP-SP14
    obligations:
      - key: IIP-SP14.a
        roles: [sp]
        level: SHOULD
        summary_en: "Support the SAML V2.0 SingleLogout profile"
      - key: IIP-SP14.b
        roles: [sp]
        level: MUST
        condition: "declared_features.single_logout == true"   # ★ 条件付き MUST
        summary_en: "If claiming SLO support, be capable of issuing logout requests"
      - key: IIP-SP14.c
        roles: [sp]
        level: OPTIONAL
        summary_en: "Consumption of logout requests is optional"

  - id: IIP-SP12
    obligations:
      - key: IIP-SP12.a
        roles: [sp]
        level: MUST_NOT
        summary_en: "Do not overload persistent NameIDs with additional semantics"
        testability: NOT_OBSERVABLE        # ★ ケースを持たないことが正しい
        not_observable_reason_en: >
          The internal semantics a deployment attaches to a persistent NameID are not
          exposed through the SAML protocol surface. No external black-box test can
          distinguish a compliant from a non-compliant generator.
```

#### `condition` は述語の種類ごとに評価する

```yaml
      # CAPABILITY_BASED — 実際の能力が条件。観測材料が必須
      - key: IIP-SP15.a
        roles: [sp]
        level: MUST
        condition:
          predicate: supports_single_logout
          predicate_kind: CAPABILITY_BASED
          declared: declared_features.single_logout
          observed:                                # ★ CAPABILITY_BASED では必須
            - target_metadata_has: "md:SPSSODescriptor/md:SingleLogoutService"
            - observed_message: LogoutRequest

      # CLAIM_BASED — 「対応を表明しているか」自体が条件。observed は不要
      - key: IIP-SP14.b
        roles: [sp]
        level: MUST
        condition:
          predicate: claims_single_logout
          predicate_kind: CLAIM_BASED
          declared: declared_features.single_logout
          observed: []                             # ★ 空でよい（申告が真理値そのもの）

      # CLASSIFICATION_BASED — 製品分類が条件。明示的な除外申告のみが FALSE を作れる
      - key: IIP-IDP13.a
        roles: [idp]
        level: MUST
        condition:
          predicate: not_token_translation_proxy
          predicate_kind: CLASSIFICATION_BASED
          declared: target.kind
          observed: []                             # ★ 観測材料は存在しない
          declaration_only_exclusion:              # ★ この節がなければ FALSE を採れない
            allowed: true
            requires_reason: true                  # 利用者に理由の記入を求める
            statement_en: >
              The target was declared to be a token translation Proxy,
              to which IIP-IDP13 does not apply. This was not verified.
```

評価規則（[03 §1](03-test-model.md) が正）:

| `predicate_kind` | `observed` | 観測材料なしで `declared = false` のとき |
|---|---|---|
| `CLAIM_BASED` | 空でよい | `FALSE` を採用（申告が真理値そのもの） |
| `CAPABILITY_BASED` | **必須** | `UNKNOWN` → `NOT_VERIFIED(applicability_undetermined)` |
| `CLASSIFICATION_BASED` | 空でよい | 既定 `UNKNOWN`。`declaration_only_exclusion.allowed: true` かつ**明示的な除外申告**があるときのみ `FALSE`。`basis: declaration_only_exclusion` を記録し、`run.conformance` は `CONFORMANT_WITH_DECLARED_EXCLUSIONS` になる |

- 評価は `effective_result`（TRUE/FALSE/UNKNOWN）と `conflict`（bool）を**独立に**返す
- 述語は `predicates.yaml` に列挙した固定集合のみ。任意コードは書けない

### 2.2 テスト定義 `tests/defs/*.yaml`

```yaml
id: IIP-MD04-02
obligation: IIP-MD04.a                 # ★ requirement ではなく obligation を指す
title: "Reject metadata whose validUntil has already passed"
title_ja: "validUntil が過去のメタデータを拒否する"

mode: CONFIG                           # AUTOMATED | BROWSER | ATTESTED | CONFIG
configuration_failure_semantics: normative_capability
  # normative_capability = 設定できること自体が義務（能力なし → outcome: violated）
  # test_precondition    = 設定はテスト成立の前提にすぎない（能力なし → outcome: not_verified）
  # [03 §4] の共通判定手順を参照
security_relevant: false

requires:
  plan_options:
    suite_metadata_delivery: [http_url, mdq]
    # 満たさない場合は NOT_VERIFIED(plan_configuration)。NOT_APPLICABLE にはしない
  reachability: target_to_suite        # [07 §2] 到達性が確認済みであること
  passed_cases: [IIP-SSO01-01]
  session: none                        # none | required | any
  destroys_session: false

setup:
  suite_metadata_variant: expired-valid-until
  parameters:
    expired_by_seconds: 86400

instructions:
  en: |
    1. In your product, force a refresh of the Suite's metadata.
    2. Then attempt a login through this Test Plan.
  ja: |
    1. 対象製品側で Suite のメタデータを再読込してください。
    2. その後、この Test Plan からログインを実行してください。

expected:
  en: >
    The target must refuse to load or use the expired metadata. Evidence: a SAML Status
    error, an HTTP 4xx/5xx, or an operator-confirmed error condition. Successfully
    completing SSO while the expired metadata is published is a failure of this obligation.

evidence_ladder: [L1, L2, L4]

attestation:
  question_en: "Did the target refuse the metadata (error shown, no SSO)?"
  options:
    - { value: refused,  outcome: satisfied }
    - { value: accepted, outcome: violated }
    - { value: unclear,  outcome: indeterminate }

implementation: org.samlier.tests.md.ExpiredValidUntilCase
tags: [metadata, trust]
```

### 2.3 ★ ケースは Verdict を直接返さない

テスト定義とケース実装が返すのは **`outcome`（義務を満たしたか）** であり、
`PASS` / `FAIL` / `WARNING` ではない。

**レベルの正規化**（スキーマが許す 8 値を 3 クラスに畳む）:

| `level` の値 | クラス | 根拠 |
|---|---|---|
| `MUST`, `MUST_NOT`, `REQUIRED` | `MUST_CLASS` | RFC2119 §1, §2, §3 — 絶対要件 |
| `SHOULD`, `SHOULD_NOT`, `RECOMMENDED`, `NOT_RECOMMENDED` | `SHOULD_CLASS` | RFC2119 §3, §4 — 推奨 |
| `MAY`, `OPTIONAL` | `MAY_CLASS` | RFC2119 §5 — 任意 |

`MUST_NOT` / `SHOULD_NOT` / `NOT_RECOMMENDED` は**禁止**の義務である。
`outcome: satisfied` は「禁止された振る舞いをしなかった」を意味する。
極性の反転はケース実装が行い、`outcome` に到達した時点では既に
「義務を満たしたか」に正規化されている。

**変換表**:

| `outcome` | `MUST_CLASS` | `SHOULD_CLASS` | `MAY_CLASS` |
|---|---|---|---|
| `satisfied` | `PASS` | `PASS` | `PASS` |
| `satisfied_with_note` | `WARNING` | `WARNING` | `WARNING` |
| `violated` | `FAIL` | `WARNING` | `NOT_SUPPORTED` |
| `indeterminate` | `INDETERMINATE` | `INDETERMINATE` | `INDETERMINATE` |
| `inconsistent` | `INCONSISTENT` | `INCONSISTENT` | `INCONSISTENT` |
| `not_verified(r)` | `NOT_VERIFIED(r)` | `NOT_VERIFIED(r)` | `NOT_VERIFIED(r)` |

★ `outcome: violated` + `reason_code: capability_absent`（設定能力の欠如）も
この表に**そのまま従う**。MUST なら FAIL、**SHOULD なら WARNING**、MAY なら NOT_SUPPORTED。
共通判定手順を通しても、変換は必ず Evaluator が行う（[03 §4](03-test-model.md)）。

- `satisfied_with_note` は「義務は満たしたが運用上の注意がある」。
  MUST 義務に `WARNING` が付く唯一の経路であり、[03 §7.2](03-test-model.md) の
  `satisfied(o) ≡ verdict ∈ {PASS, WARNING}` と対応する
- `MAY_CLASS` の `violated` が `NOT_SUPPORTED` になるのは、
  任意機能を実装していないだけだからである

Verdict への変換は Runner が `coverage.yaml` のレベルを見て一元的に行う
（[03 §7.5](03-test-model.md) の `Evaluator`）。
**ケース実装に FAIL/WARNING を判断させない**ことで、
「原文は SHOULD なのにテストが FAIL を返す」というクラスのバグを構造的に防ぐ。

## 3. 仕様カタログ

`spec.document` の値は別ファイルで一元管理し、URL やタイトルの変更を 1 箇所に閉じ込める。

```yaml
# tests/specs.yaml
kantara-fedinterop-impl:
  title: "SAML V2.0 Implementation Profile for Federation Interoperability"
  publisher: "Kantara Initiative"
  versions:
    "1.1":
      date: 2019-12-18
      url: https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html
      anchor_pattern: "#{anchor}"
oasis-saml-core:
  title: "Assertions and Protocols for the OASIS SAML V2.0"
  ...
```

## 4. Java 実装側のインターフェース

### 4.1 ★ ケースは中断・再開できなければならない

ケースは実行の途中で `WAITING_BROWSER` / `WAITING_CONFIG` / `WAITING_ATTEST` に入る。
再開は**別の HTTP リクエスト**で起こり、Suite の再起動を挟むこともある。
Java の呼び出しスタックはそこまで保持できないため、
`CaseOutcome execute(ctx)` のような同期 API では成立しない。

明示的な状態遷移にする。

```java
public interface TestCaseImpl {
    /** YAML の id と一致すること。CI で検証される。 */
    String id();

    /** 最初のステップ。 */
    CaseStep start(CaseContext ctx);

    /**
     * 中断からの再開。state は SQLite に永続化されていたもの。
     * 同じ (state, event) で複数回呼ばれても同じ結果になること（冪等）。
     */
    CaseStep resume(CaseContext ctx, CaseState state, CaseEvent event);
}

/** 次に何を待つか。 */
public sealed interface CaseStep {
    record Continue(CaseState next)                                   implements CaseStep {}
    record AwaitBrowser(CaseState next, URI startUrl, Duration ttl)   implements CaseStep {}
    record AwaitConfig(CaseState next, String instructionKey, Duration ttl) implements CaseStep {}
    record AwaitAttestation(CaseState next, String questionKey, Duration ttl) implements CaseStep {}
    record AwaitInbound(CaseState next, InboundMatcher m, Duration ttl) implements CaseStep {}
    record Finish(CaseOutcome outcome)                                implements CaseStep {}
}

/** Suite が受け取った出来事。 */
public sealed interface CaseEvent {
    record InboundMessage(InboundSamlMessage msg)  implements CaseEvent {}
    record BrowserReturned(String acsPath)         implements CaseEvent {}
    record ConfigConfirmed()                       implements CaseEvent {}
    record Attested(String value, String note)     implements CaseEvent {}
    record TimedOut(Duration waited)               implements CaseEvent {}
    record Aborted(String reason)                  implements CaseEvent {}
}

/** ケースが持ち越す状態。JSON にシリアライズして SQLite に保存する。 */
public record CaseState(String phase, Map<String, Object> data) {}

/** ケースが返すのは義務の充足状況であり、Verdict ではない（§2.3）。 */
public record CaseOutcome(
    Outcome outcome,                 // satisfied | violated | indeterminate | inconsistent | not_verified
    String notVerifiedReason,        // outcome == not_verified のとき必須
    String reasonCode,               // 機械可読。例: "metadata.accepted-expired"
    String reasonMessageKey,
    List<EvidenceRef> evidence,
    Map<String, Object> details
) {}
```

### 4.2 実装が守る規約

| 規約 | 理由 |
|---|---|
| `CaseState` は JSON 化できる値のみを持つ | 再起動をまたぐ |
| `resume` は冪等 | 再送・リトライ・二重クリックで壊れない |
| 各 `AwaitXxx` に TTL を必ず付ける | タイムアウトで `NOT_VERIFIED(timeout)` に落とす |
| 副作用は**直接実行せず、`CaseStep` に「送信意図」として載せる** | クラッシュ整合性（§4.4） |
| `Finish` 以外で `CaseOutcome` を作らない | 判定点を 1 箇所にする |
| ケース実装は `System.currentTimeMillis()` を直接使わない | クロックスキューテストで時刻を操作する |

### 4.3 ★ クラッシュ整合性: 送信は outbox 経由にする

`resume` を「冪等」と宣言するだけでは二重送信を防げない。
**送信した直後・状態を保存する前**にプロセスが落ちると、再開時にどうなるか決まらない。

そこで**ケース実装は自分で送信しない**。送信意図を返し、Runner が outbox で実行する。

```java
public sealed interface CaseStep {
    /** 送信意図を伴う遷移。next と actions は同一トランザクションで永続化される。 */
    record Continue(CaseState next, List<OutboundAction> actions) implements CaseStep {}
    record AwaitBrowser(CaseState next, List<OutboundAction> actions, URI startUrl, Duration ttl) implements CaseStep {}
    ...
}

public record OutboundAction(
    String actionId,              // state から決定論的に導出（乱数・時刻を使わない）
    OutboundKind kind,
    byte[] payload,
    URI target,
    boolean requiresEphemeralCredential   // §4.3.2
) {}
```

Runner の実行順序:

```
① BEGIN TRANSACTION
     - case_state を next に更新
     - outbox に actions を (actionId, PENDING) で挿入
   COMMIT                       ← 「送るつもりだった」が確定
② outbox の PENDING を SENDING に更新してから送信
③ 送信完了 → SENT（送信結果・Transcript 参照つき）
```

### 4.3.1 ★ exactly-once は保証できない — `UNKNOWN_DELIVERY` を持つ

前版は「同一 SAML `ID` で再送すればリプレイ検出に引っかからない」と書いていたが、
**これは逆である**。同一 `ID` の再送こそがリプレイ検出の対象であり、
正しく実装された対象は 2 通目を**拒否する**。
つまり再送は「対象が正しいほど失敗する」。

ネットワーク送信と `SENT` 更新は原子的にできない以上、
**配信済みか不明な状態が構造的に存在する**。これを型で表す。

```
PENDING           まだ送っていない          → 安全に送れる
SENDING           送信を開始した            → クラッシュ後は UNKNOWN_DELIVERY へ
UNKNOWN_DELIVERY  届いたか分からない  ★     → 下記の規則に従う
SENT              送信完了を確認した        → 再送しない
```

`UNKNOWN_DELIVERY` からの復帰規則:

| 状況 | 扱い |
|---|---|
| そのケースが inbound を待つ設計（`AwaitInbound` / `AwaitBrowser`） | **まず待つ**。対象からの応答が来れば届いていたと分かり `SENT` に確定。TTL 内に来なければ下段へ |
| ★ `OutboundKind` が **Runner の再送可能 allowlist** に含まれる | 再送する（下記） |
| 上記以外 | **再送しない**。ケースを `NOT_VERIFIED(delivery_unknown)` で終える |
| 再送して対象が **replay エラー**（`urn:oasis:names:tc:SAML:2.0:status:Requester` 等 + リプレイを示す理由）を返した | ★ **これを対象の不適合として扱わない**。ケースは `NOT_VERIFIED(delivery_unknown)`。**Samlier 側の障害を対象の FAIL にしてはならない** |
| 利用者に確認できる状況（対話中） | 「対象側でこの操作が実行されましたか?」と確認して分岐 |

#### 再送可否はケースに宣言させない

前版は `replay_safe: true` をテスト定義に書かせる設計だったが、
**CI は対象側の副作用まで証明できない**（「実際に冪等な操作しか行っていない」ことは
静的解析では確かめられない）。ケース作者が誤って付けると、
Suite が対象の状態を壊したうえで対象を FAIL と表示しうる。

再送可否は **`OutboundKind` 単位で Runner が固定する**。ケースは選べない。

```java
enum OutboundKind {
    METADATA_FETCH   (Retry.SAFE),    // GET。対象の状態を変えない
    MDQ_FETCH        (Retry.SAFE),    // 同上
    AUTHN_REQUEST    (Retry.UNSAFE),  // 対象がリプレイ検出しうる
    LOGOUT_REQUEST   (Retry.UNSAFE),  // セッションを壊す
    ECP_SOAP         (Retry.UNSAFE),  // 認証試行。ロックアウトの恐れ
    SOAP_SLO         (Retry.UNSAFE);
}
```

- `Retry.SAFE` は **HTTP GET かつ SAML の状態を持たないもの**に限る。
  新しい `OutboundKind` に `SAFE` を付けるには、コードレビューでの承認が要る
- `Retry.UNSAFE` の `UNKNOWN_DELIVERY` は **再送せず** `NOT_VERIFIED(delivery_unknown)`
- 迷ったら `UNSAFE`。1 ケースが未検証になるほうが、対象を壊すよりずっとよい

- `UNKNOWN_DELIVERY` が発生した Run は、結果 JSON の
  `suite_incidents[]` に記録する。**対象の評価とは別枠**で残す
- `actionId` は `runId + caseId + state.phase + 連番` から導出する。
  `UUID.randomUUID()` / `System.nanoTime()` は静的解析で禁止（CI 規則 26）
- SAML メッセージの `ID` も `actionId` から導出する。
  ただしその目的は**リプレイ検出の回避ではなく**、
  「同じ action が 2 回送られたことを対象側のログからも同定できるようにする」ためである

> 目標は exactly-once ではなく、**「不確実性を対象の不適合に転嫁しない」**こと。
> Suite が自分の障害で他人の製品を FAIL と表示するのが最悪の失敗である。

### 4.3.2 ★ 永続 outbox と一時的な資格情報の両立

`OutboundAction.payload` は outbox に永続化される。
一方 ECP の HTTP Basic 資格情報（IIP-IDP14）は
**保存してはならない**（[02 §5.2](02-architecture.md), [08 §4](08-suite-security.md)）。
このままでは、資格情報を payload に入れれば保存禁止違反、
入れなければ再起動後に PENDING を実行できない。**両立していなかった。**

解決: **資格情報は payload に載せず、実行時に注入する。**

```java
// payload には資格情報のプレースホルダのみが入る
OutboundAction(actionId, ECP_SOAP, soapEnvelopeBytes, idpSoapEndpoint,
               requiresEphemeralCredential = true)
```

| 状態 | 挙動 |
|---|---|
| 資格情報がメモリにある（同一プロセス内） | Runner が送信直前に注入して実行 |
| **再起動後で資格情報がない** | action を `BLOCKED_ON_CREDENTIAL` にし、Run を **`WAITING_CREDENTIAL`** に遷移。UI が再入力を求める |
| 利用者が再入力を拒否 / TTL 超過 | ケースを `NOT_VERIFIED(credential_unavailable)` で終える |

- `WAITING_CREDENTIAL` は `WAITING_*` 系の 1 つとして状態機械に加える（[03 §8](03-test-model.md)）
- 資格情報は **Run スコープのメモリのみ**に保持し、`CaseState` にも outbox にも
  Transcript にも書かない
- 暗号化した秘密ストアに保存する案は**採らない**。
  鍵も `/data` に置かれる以上、実質的に平文保存と変わらない（[08 §4](08-suite-security.md)）
- `CredentialLeakTest`: 資格情報を投入した Run を再起動でまたいだあと、
  **`/data` 配下の全バイト列に資格情報が現れないこと**を検証する

### 4.4 CaseContext

テストが直接 HTTP を叩くことは禁止する。全て記録させる。

```java
interface CaseContext {
    SamlMessageBuilder authnRequest();      // OpenSAML ベース（正常系）
    RawMessageBuilder  rawMessage();        // DOM 直接（Phase 4 の足場）
    MetadataControl    metadata();          // variant 切替、MDQ 応答の制御
    HttpExchange       fetch(URI uri);      // SSRF ガードを通り、全て記録される
    EcpClient          ecp();               // PAOS/SOAP クライアント（02 §4）
    Clock              clock();
    PlanParameters     params();
    Reachability       reachability();      // target→suite が確認済みか（07 §2）
    Transcript         transcript();
}
```

## 5. CI で強制する整合性

```
[カタログ]
 1. coverage.yaml の全 requirement id が仕様カタログの既知 ID であり、69 件そろっている
 2. 全 obligation key が一意で、`<requirement>.<a|b|c…>` 形式である
 3. 全 obligation に roles / level / summary_en がある
 4. level が MUST|MUST_NOT|REQUIRED|SHOULD|SHOULD_NOT|RECOMMENDED|MAY|OPTIONAL のいずれか
 5. condition の predicate が `predicates.yaml` の定義済み集合に含まれる
 5b. `predicate_kind` が全ての condition に存在する
 5b-1. `CAPABILITY_BASED` の condition は**空でない `observed`** を持つ
 5b-2. `CLASSIFICATION_BASED` かつ `level ∈ MUST_CLASS` の condition は
       `declaration_only_exclusion` ブロック（`allowed` / `requires_reason` / `statement_en`）を持つ
 5b-3. **［:specReconcile］** `CLAIM_BASED` は、**その義務の `source_clause` が指す句**に
       *claim(s) support* 相当の語がある義務にのみ使える
       （節全体で検査すると、同じ節の別義務での誤用を見逃す）
       （`:specReconcile` が取得した原文の該当節にその語があることを確認する。
        ダイジェストはハッシュなので語の検査には使えない）
 5b-4. ★ **［:specReconcile］** 除外述語（`CLASSIFICATION_BASED`）を持つ全ての義務について、
       その義務の `source_clause` または**同じ節の末尾**に除外文が含まれている
       （除外範囲が隣接要件に広がるのを防ぐ。[03 §1](03-test-model.md)）
 5c. `predicates.yaml` に定義のない predicate が使われていない
 5d. ★ `mode: CONFIG` の全ケースが `configuration_failure_semantics`
     （`normative_capability` | `test_precondition`）を持つ（[03 §4](03-test-model.md)）
 6. testability: NOT_OBSERVABLE の obligation は not_observable_reason_en を持ち、
    かつ **テストケースを 1 件も持たない**
 6b. 全 obligation に review ブロックがあり、`state` は **常に `PENDING_REVIEW`**。
     ★ **承認は `coverage.yaml` に書かない**。正本は署名済みの `tests/approvals/g1.yaml`
     （承認対象 commit の外）であり、`reviewer` / `approved_at` はそちらに入る
     （[03 §7.5](03-test-model.md), [tools/ci-stages.md](../tools/ci-stages.md)）
 6c. 全 obligation に `authored_by` と、`review.{state, reviewer, approved_at, source_spec,
     spec_version, source_selector, source_section_digest}` がある。
     句の位置は obligation 直下の **`source_clauses[]`（start / end / digest / occurrences）**
     に持つ（複数範囲を許す。共有の lead-in と個別の item を別範囲で記録できる）
 6c-0. `source_clauses[]` の各要素が `0 ≤ start < end`（非空、Unicode コードポイント単位）であること
 6c-2. 各 `source_clauses[]` の `occurrences` が 1 であること。2 以上なら locator が曖昧であり、
       起票側で一意な句に直すか出現位置を明示する（`g1_validate.py` の SR-11 / SR-12）
       ★ **`end ≤ 節長` はオフラインでは検証できない**（原文をリポジトリに置かない設計のため
       節長を知りようがない）。この検査は 6c-1 の :specReconcile 側で行う
 6c-1. **［:specReconcile のみ・ネットワーク要］** source_selector が選んだ節を正規化した
       ダイジェストが source_section_digest と一致し、**`end ≤ 節長` であり**、かつ
       **source_clause の範囲の部分文字列のダイジェストが source_clause.digest と一致する**
       （いずれか不一致なら承認失効）。
       原文はリポジトリに保存せず build/spec-cache/ に取得する
       （[09 D-11](09-open-decisions.md) の全文非転載と両立させるため。[04 G1](04-requirement-coverage.md)）
 6d. review.spec_version が specs.yaml の現行版と一致する
 7. testability が NOT_OBSERVABLE 以外の obligation は 1 件以上のケースを持つ
    （持たない場合、リリースブロッカーとして落ちる ＝ NOT_VERIFIED(not_implemented) の撲滅）

[テスト定義]
 8. 全 YAML の id が一意
 9. 全 YAML の obligation が coverage.yaml に存在する
10. 全 YAML の implementation クラスが存在し TestCaseImpl を実装し id() が一致する
11. 全 TestCaseImpl 実装に対応する YAML がある（孤児実装の禁止）
12. mode: ATTESTED または evidence_ladder に L4 を含むケースは attestation ブロック必須
13. attestation.options の outcome が Outcome の値である
14. instructions.en / expected.en が全ケースに存在する（ja は任意）
15. requires.passed_cases に循環がない
16. **テスト定義に level / verdict を書いていない**（判定レベルは coverage.yaml のみ）

> **［:specReconcile］** と付いた規則は**ネットワークを使う別ジョブ**で実行する。
> 日常の `./gradlew check` はオフラインで完結させ、CI の定期ジョブと
> リリース前に `:specReconcile` を必ず走らせる（[04 G1](04-requirement-coverage.md)）。

[判定ロジック]
17. VerdictAggregationTest: 10 値 × 10 値の全組み合わせが [03 §6.4](03-test-model.md) の決定表と一致
18. RunVerdictTest: FAIL / NOT_VERIFIED / NOT_OBSERVABLE / INCONSISTENT の各組み合わせで
    Run 判定が [03 §7.2](03-test-model.md) と一致
19. OutcomeToVerdictTest: outcome × level → Verdict の写像が [§2.3](#23-ケースは-verdict-を直接返さない) と一致
20. NotApplicableGuardTest: **NOT_APPLICABLE が「役割違い」と「条件付き義務の条件が偽」
    以外の経路から発生しない**ことをコードパスで保証する
20b. ★ **CapabilityBranchTest**: `mode: CONFIG` の全ケースが
     [03 §4 の共通判定手順](03-test-model.md)を通ること。ケースが返すのは **outcome** であり、
     `capability_absent` は `outcome: violated` の reason_code である
     （`normative_capability` の場合）。ケース実装が Verdict を返す型を持たないことを
     コンパイル時に保証し、独自に not_verified 系を返す経路を静的解析で禁止する
20c. `outcome: violated` + `reason_code: capability_absent` が、
     `obligation.level` に応じて **MUST→FAIL / SHOULD→WARNING / MAY→NOT_SUPPORTED** に
     変換されることをテーブル駆動テストで検証する（**SHOULD を FAIL にしない**）
20d. 条件付き義務の `effective_result == FALSE` のとき、そのケースが実行されない
     （適用性の評価がケース実行より先。例: IIP-ALG05.b は CBC 非対応なら NOT_APPLICABLE）

29. リリース系タスク（`release` / `publish` / `dockerPush`）が `:specReconcile` に
    依存しており、その実行で生成されたレポートのみを受け付ける
    （[04 G1](04-requirement-coverage.md)。運用規約に頼らない）

[生成物]
21. docs/04-requirement-coverage.md が coverage.yaml から生成した内容と一致する
    （手編集を検出して落とす）
22. 集計値（Phase 1 実装件数など）は生成のみ。手書きの数値がドキュメントに残っていない
23. **ドキュメント中の result.json 例が Evaluator の出力と一致する**
    （golden fixture。手書きの JSON 例を禁止する。[03 §7.5](03-test-model.md)）
24. result.json が JSON Schema (`schema/result-v1.json`) に適合する
24b. **`advisories[].affects_verdict` が `false` 以外の値を取れない**（スキーマで固定）。
     advisory が Verdict・coverage・conformance のいずれにも影響していない
25. golden fixture の Run 判定が [03 §7.2](03-test-model.md) の規則と一致する
    （例: must が未解決なら CONFORMANT 系にならない、SP 専用義務が IdP Run に現れない）
26. **outbox 規約**: `OutboundAction.actionId` が state から決定論的に導出されている
    （`UUID.randomUUID()` / `System.nanoTime()` の使用を静的解析で禁止）
27. ケース実装が `ctx` 以外の経路で HTTP を発行していない（送信は outbox のみ）
27b. `requiresEphemeralCredential` な action の payload に資格情報が含まれていない
     （`CredentialLeakTest`。§4.3.2）
27c. テスト定義に `replay_safe` 相当のフィールドが存在しない
     （再送可否は `OutboundKind` の allowlist のみで決まる）
28. **依存仕様の版が固定されている**: `specs.yaml` の各エントリに version / date / URL があり、
    外部ドラフト（SAML-EC 等）を参照する義務はその版まで指定している
```

> 17〜20 が [レビュー指摘 1・2](10-memo-review.md) への構造的な歯止めになる。
> 規則を文書に書くだけでは、実装で必ずずれる。

## 6. テスト定義の公開

テスト定義は Suite のリポジトリに含まれ、Web UI からも読める。
結果ページの各項目から、そのテストの YAML 全文にリンクする。

> これが「認定機関を名乗らずに信頼を得る」ための中心的な手段になる。
> 判定に納得できない人が、**何を根拠に何をテストしたのかを完全に読める**こと。
