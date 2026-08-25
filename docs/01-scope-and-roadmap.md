# 01. スコープとロードマップ

## フェーズ全体

```
Phase 1  Implementation Conformance      Kantara IIP v1.1            ← 今ここ
   ↓
Phase 2  Core Conformance                OASIS SAML Core / Bindings / Profiles
   ↓                                     + OASIS Conformance Requirements の適合クラス
Phase 3  Deployment / Interoperability   Kantara Deployment Profile v2.0（旧 SAML2int）
   ↓                                     + eduGAIN / InCommon 等の実運用要件
Phase 4  Security / Attacker Model       OASIS Security & Privacy Considerations
   ↓
Phase 5  Fuzzing / Differential Testing
```

> **元メモからの訂正**: Phase 3 の「SAML2Int」は現在 Kantara Initiative の
> *SAML V2.0 Deployment Profile for Federation Interoperability v2.0* に引き継がれている。
> saml2int.org の v0.2.1 は歴史的文書として扱い、参照先は Kantara 版にする。

## Phase 1 のスコープ

### 含む

| 領域 | 内容 |
|---|---|
| プロファイル | SAML IdP Implementation Profile / SAML SP Implementation Profile（各 2 レベル、後述） |
| バインディング | HTTP-Redirect、HTTP-POST、SOAP（バックチャネル SLO / ECP） |
| プロトコル | Web Browser SSO、Single Logout、Enhanced Client or Proxy (ECP) |
| メタデータ | 静的検査 + Suite からの配布（HTTP / MDQ）+ 署名 / validUntil / 複数鍵 |
| 暗号 | 署名・暗号アルゴリズムの対応確認とアルゴリズム宣言の遵守 |
| 実行 | Web UI からの Test Plan 作成、対話的実行、通信ログの完全記録 |
| 出力 | HTML レポート、JSON 結果、opt-in の共有 URL |

### 含まない（Phase 1）

| 除外 | 理由 / 先送り先 |
|---|---|
| 攻撃テスト（XSW、署名偽装、リプレイ等） | Phase 4。ただし基盤（低レベル XML 生成）は Phase 1 で用意する |
| Artifact バインディング | IIP v1.1 の必須要件ではない。Phase 2 |
| SAML Attribute Query / AuthzDecisionQuery | Phase 2 |
| IdP Proxy / Gateway の特殊挙動 | Phase 3 |
| ブラウザ自動操作（Playwright 等） | Phase 2 以降。Phase 1 は利用者のブラウザを使う |
| 多言語 UI | 英語のみ |
| ユーザーアカウント / 権限管理 | Hosted 版で必要になった時点で。Phase 1 self-hosted は認証なし |
| CI 連携（GitHub Action 等） | Phase 2。ただし結果 JSON の安定化は Phase 1 で行う |

## Test Profile の構造

```
SAML Implementation Profile (Kantara IIP v1.1)
│
├── IdP
│   ├── IdP Core     — Common(31) + IdP(21) のうち MUST かつ SSO/Metadata/Algorithm
│   └── IdP Full     — 上記 + SLO + ECP + SHOULD/RECOMMENDED 要件
│
└── SP
    ├── SP Core      — Common(31) + SP(17) のうち MUST かつ SSO/Metadata/Algorithm
    └── SP Full      — 上記 + SLO + SHOULD/RECOMMENDED 要件
```

> **元メモからの改良 + レビュー反映**: メモは "Basic / Full" としていたが、
> 区別の基準が書かれていなかった。基準を置く。**割り当ての単位は要件ではなく義務**。

```
Full(role)  = その役割に適用される全ての義務（NOT_APPLICABLE を除く）
              ★ 除外はしない。定義上、プロファイル全体
Core(role)  ⊂ Full(role)
              Samlier が「相互運用の最低ライン」として選んだ部分集合
```

**Core の選定基準**（`coverage.yaml` の `level_assignment` に義務ごとに記録する）:

1. level が `MUST_CLASS` であること
2. かつ、次のいずれかに該当すること
   - Web Browser SSO の成立に直接必要（SSO / Bindings / NameID / 署名の位置）
   - メタデータの取得・検証・鍵の扱い（**IIP-MD01〜MD12 を含む**）
   - 署名・暗号アルゴリズムの相互運用（ALG01〜ALG06, ALG08）
   - 属性の受け渡しの基本（SP01, SP02, SP10, IDP01）
3. かつ、SLO / ECP / Discovery に属さないこと

> **訂正**: 前版は「MUST の Metadata は全て Core」と書きながら、
> カバレッジ表で IIP-MD02 を Full にしていた（レビュー指摘 13）。
> **IIP-MD02 は Core** に修正した。
> また前版の Full の定義は役割固有の MUST（属性リリース、Discovery 等）を
> 取りこぼしていた。**Full = 全義務**と定義し直したことで漏れが起きない。

`level_assignment` は **要件単位ではなく義務単位**で持つ。
同じ要件の中で `.a` が Core、`.b` が Full になることがある
（例: IIP-SP13.a は Core、IIP-SP13.b（既定で拒否・SHOULD）は Full）。

Core / Full の割り当ては `coverage.yaml` を正とし、
[04-requirement-coverage.md](04-requirement-coverage.md) はそこから生成する。
**IIP 原文に Core/Full の区別はない**（Samlier 独自の分類である）ことをレポートに明記する。

## v0.1 のマイルストーン

**決定**: v0.1 = Phase 1 完全（IIP v1.1 の全 69 要件、SLO / ECP を含む）。
初リリースまでが長くなるため、内部マイルストーンで進捗を可視化する。

> **「全 69 要件」の意味**（[04](04-requirement-coverage.md) と揃える）
> - ✅ 全 69 要件を**義務単位に分解して収録**し、レポートに出す
> - ✅ `testability: NOT_OBSERVABLE` **以外の全ての義務**にテストケースを実装する
>   （`NOT_VERIFIED(not_implemented)` が 0 件であることを CI で強制）
> - ❌ 「全ての Run で 69 要件が判定される」ことは意味しない。
>   Test Plan の構成・到達性・対象側の設定可否により `NOT_VERIFIED` が残り、
>   その場合 `conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる

| M | 内容 | 完了の目安 |
|---|---|---|
| **G1a** 作成 ✅ | 全 69 要件を原文の節末まで読み、133 義務に分解。`tests/{specs,coverage,predicates}.yaml` と `docs/04`（生成物）を作成 | **完了（PENDING_REVIEW）** |
| **G1b** 承認 ⏳ | **作成者以外**が原文と `coverage.yaml` を直接照合して全義務を承認（`reviewer` / `approved_at` を記入） | 判定の正本が確定する。**ここを通るまでテスト実装に着手しない** |
| **M0** 骨格 | Test Peer のメタデータ発行、Transcript Recorder、Preflight、Test Plan の CRUD、SSE。テスト 0 件でも「Keycloak と SSO が 1 往復する」ところまで | Suite が SAML の相手役として成立する |
| **M1** SSO コア | Common の SSO / Algorithms + IdP/SP の SSO 要件。判定語彙・証拠ラダー・attestation UI を実装 | 「クイック実行」モードが完成。ここで一度リファレンス実装 3 つに当てて検出力を確認する |
| **M2** メタデータ | Suite からのメタデータ配布 / MDQ / variant（IIP-MD01〜12）。`WAITING_CONFIG` ステップ | 対象側の再設定を伴うテストが回る |
| **M3** SLO + ECP + 残件 | IIP-SP14〜17 / IIP-IDP13〜21。ECP は **ECP クライアント + SP** を演じてバックチャネルのみで自動化（[02 §3.7](02-architecture.md)）。IIP-SP05 用の `secondary_peer`（2 つ目の Test IdP）もここ | `NOT_VERIFIED(not_implemented)` が 0 件になる |
| **M4** 公開 | 結果 JSON v1 凍結、`report.html`、Hosted 版、共有 URL、公開前スクラブ | **v0.1 リリース** |

> **M1 の時点で必ず一度リファレンス実装に当てる**こと。
> 全部作ってから検出力がないと分かるのが最悪のパターン。
> M1 で Keycloak / Shibboleth / SimpleSAMLphp の結果に差が出ないなら、判定設計を見直す。

**「クイック実行」モード（v0.1 必須）**
対象側の再設定を要するテスト（`mode: CONFIG`）を全て飛ばし、
メタデータ登録 1 回 + ログイン 1 回で終わる約 10 分のプリセット。
スコープを完全にしても、初回体験が 1 時間かかる設計にはしない。

> **ただしクイック実行の結果は「適合」を名乗れない。**
> 飛ばした義務は `NOT_VERIFIED(plan_configuration)` として母数に残り、
> MUST が含まれるため `conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる（[03 §7.2](03-test-model.md)）。
> UI で「これは動作確認であり適合判定ではありません」と明示する。

## Phase 1 完了の判定

Phase 1 は次が揃った時点で完了とする。

1. [04](04-requirement-coverage.md) の表で Phase 1 対象とした全要件にテスト定義とその実装がある
2. 外部から検証不能と分類した要件が、レポート上でそれと分かる形で出ている
3. リファレンス実装 3 つ（Keycloak / Shibboleth IdP / SimpleSAMLphp）に対する実行結果が公開されている
4. `docker run` 一発で起動し、README に沿って初回テストが完了できる
5. 結果 JSON スキーマが v1 として凍結され、破壊的変更にはバージョン上げが必要な運用になっている

## Phase 4（将来）— Security Profile の骨子

Phase 1 の Test Runner 基盤がそのまま使える。攻撃者能力から逆算してテストを定義する。

```
Attacker capabilities                Derived tests
├─ 正当な Assertion を入手できる    → Assertion replay / Response replay
├─ XML を改変できる                 → XML Signature Wrapping (XSW1-8)
│                                     署名除去 / Status 改竄 / 署名 exclusion
├─ 悪意ある IdP になれる            → Wrong Issuer / IdP confusion / 未登録 IdP からの Assertion 受理
├─ 悪意ある SP になれる             → Assertion の他 SP への転送（Audience 無視）
├─ メタデータ経路に介入できる       → Metadata key substitution / validUntil 無視
└─ 中間者になれる                   → Algorithm downgrade / TLS 検証不備

その他: Wrong Audience / Wrong Destination / Expired Assertion / NotBefore 未来 /
        Invalid signature / 未署名 Response 受理 / XXE / Billion laughs /
        Comment truncation attack (CVE-2017-11427 系) / NameID 正規化差異
```

> Phase 4 のためには **OpenSAML が受け付けない不正 XML を意図的に生成できること**が必須。
> この要求は Phase 1 の [02-architecture.md](02-architecture.md) の SAML Engine 設計に織り込む。
