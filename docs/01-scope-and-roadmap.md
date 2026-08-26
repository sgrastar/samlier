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
| **G1b** 承認 ⏳ | **作成者以外**が原文と `coverage.yaml` を直接照合し、**署名済みの `tests/approvals/g1.yaml`**（承認対象 commit の外）で全義務を承認する。`coverage.yaml` は編集しない | `g1_ci_verify.sh` が `g1.complete == true` を返す |
| **G2** テスト設計 ⏳ | 132 義務を**ケース ID に割り当て**、`required_variants` の網羅と positive/negative control を定義。**作成者以外が設計をレビュー**（[G2 の詳細](#-設計ゲート-g2--テスト設計)） | 「義務は正しいがケースに検出力がない」を防ぐ |
| **M0** 骨格 | Test Peer のメタデータ発行、Transcript Recorder、Preflight、Test Plan の CRUD、SSE。テスト 0 件でも「Keycloak と SSO が 1 往復する」ところまで | Suite が SAML の相手役として成立する |
| **M1** SSO コア | Common の SSO / Algorithms + IdP/SP の SSO 要件。判定語彙・証拠ラダー・attestation UI を実装。**G2 完了が前提** | 「クイック実行」モードが完成。**mutant peer** で検出力を確認する（[00 §5](00-concept.md)） |
| **M2** メタデータ | Suite からのメタデータ配布 / MDQ / variant（IIP-MD01〜12）。`WAITING_CONFIG` ステップ | 対象側の再設定を伴うテストが回る |
| **M3** SLO + ECP + 残件 | IIP-SP14〜17 / IIP-IDP13〜21。ECP は **ECP クライアント + SP** を演じてバックチャネルのみで自動化（[02 §3.7](02-architecture.md)）。IIP-SP05 用の `secondary_peer`（2 つ目の Test IdP）もここ | `NOT_VERIFIED(not_implemented)` が 0 件になる |
| **M4** 公開 | 結果 JSON v1 凍結、`report.html`、Hosted 版、共有 URL、公開前スクラブ | **v0.1 リリース** |

> **M1 の時点で必ず一度 mutant peer に当てる**こと（[00 §5](00-concept.md)）。
> 全部作ってから検出力がないと分かるのが最悪のパターン。
> **実製品の結果に「差が出るか」は検出力のオラクルにならない**ので使わない
> （3 製品が全て適合している可能性も、設定差で差が出る可能性もある）。

## ★ 設計ゲート G2 — テスト設計

G1b（義務が原文と正しく対応しているか）を通っても、
**「義務は正しいがケースに検出力がない」**という失敗が残る。
R5〜R9 のレビューで、対照のないケースが繰り返し見つかった
（SSO07 の「エラーも無視も可」、ALG04 の片方のアルゴリズムだけ、SP07 の拒否のみ）。

**G1b と M1（判定ケースの実装）の間に G2 を置く。**
M0（骨格）は G1b 後に着手してよいが、**判定ケースの実装は G2 完了後**とする。

### 対象

`coverage.yaml` の 133 義務のうち、`NOT_OBSERVABLE`（IIP-SP12.a）を除く **132 義務**。

| testability | 件数 | 備考 |
|---|---|---|
| `BROWSER` | 56 | 利用者のブラウザが必要 |
| `CONFIG` | 56 | 対象側の設定変更を依頼 |
| `ATTESTED` | 11 | 対象内部の挙動を申告 |
| `AUTOMATED` | 9 | バックチャネルのみで完結 |
| `NOT_OBSERVABLE` | 1 | ケースを作らない |

### 成果物 — `tests/cases.yaml`（機械可読）

```yaml
schema_version: 1
g2_state: PENDING_REVIEW          # G1 と同じく、承認は署名済み記録で行う
cases:
  - id: IIP-SP13-01
    obligation: IIP-SP13.a
    covers_variants: [0, 1]        # required_variants のインデックス
    role: sp
    mode: CONFIG
    milestone: M1
    controls:
      - kind: positive             # 満たす実装が PASS すること
        description_ja: 署名済み Response を送る → 受理される
      - kind: negative             # 満たさない実装が FAIL すること
        description_ja: 拒否設定にしたうえで未署名 Response → 拒否される
    counterexample_ja: >           # ★ 必須。「義務を満たさないのに PASS する実装」
      AuthnRequest の有無にかかわらず全 Response を拒否する実装。
      positive control でこれを落とす。
    depends_on: [IIP-SSO01-01]
    destroys_session: false
    detected_by_mutants: [no-signature-validation]
```

### 通過条件

- [ ] **132 義務すべてが 1 件以上のケースに割り当てられている**（CI で検証）
- [ ] 各義務の **`required_variants` が `covers_variants` で完全に網羅**されている
- [ ] 各ケースに **positive control と negative control** の両方がある
      （片方しかないケースは、その理由を `control_waiver_ja` に書く）
- [ ] 各ケースに **`counterexample_ja`**（義務を満たさないのに PASS する実装）が書かれている。
      書けないなら検出力がないので設計をやり直す
- [ ] `depends_on` に循環がなく、`destroys_session` が実行順序に反映されている
- [ ] 全ケースが **M0〜M3 のいずれか**に割り当てられている
- [ ] **実現性スパイク**が済んでいる（下記）
- [ ] **ケース作成者以外**が設計をレビューして署名承認する（G1b と同じ方式）

### 実現性スパイク（G2 で先に潰す）

実装してから「できない」と分かると設計をやり直すことになる領域。

| # | 対象 | 確かめること |
|---|---|---|
| S1 | ECP + SAML-EC | PAOS/SOAP 往復、`samlec:GeneratedKey` の生成・検査、channel bindings の 5 ケース |
| S2 | SLO | front-channel / SOAP、Async SLO 拡張、セッション破壊の順序制御 |
| S3 | MDQ / メタデータ variant | 対象に再取得させる導線、`?variant=` の切替、301/302/307 |
| S4 | `secondary_peer` | 2 つ目の entityID の発行と登録導線（IIP-SP05 / MD01.c / IDP02） |
| S5 | 生の XML 生成 | DTD 入りメッセージ、未知属性、256 文字境界、XML 属性値正規化の扱い |
| S6 | 生クエリ文字列 | HTTP-Redirect 署名検証がバイト列で成立するか（[02 §3.5](02-architecture.md)） |

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
