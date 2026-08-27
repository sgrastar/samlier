# 00. コンセプト

## 1. 課題

OIDC / OAuth には OpenID Foundation の Conformance Suite と OpenID Certification があり、
「この製品はこのプロファイルに適合している」ことを再現可能な形で示せる。

SAML にはこれに相当する、広く認知された仕組みが存在しない。
一方で、実装品質を判断する材料そのものは公開されている。

| 文書 | 発行 | 位置づけ |
|---|---|---|
| SAML V2.0 Core / Bindings / Profiles / Metadata | OASIS | 基本仕様 |
| SAML V2.0 Conformance Requirements | OASIS | 適合クラスの定義（実行可能テストではない） |
| Security and Privacy Considerations for SAML V2.0 | OASIS | 攻撃者モデルの根拠 |
| **SAML V2.0 Implementation Profile for Federation Interoperability v1.1** | Kantara Initiative (2019-12-18) | **実装者向けの相互運用要件。Phase 1 の対象** |
| SAML V2.0 Deployment Profile for Federation Interoperability v2.0 | Kantara Initiative | 配備者向け（旧 SAML2int の後継） |
| Metadata Interoperability Profile | OASIS | メタデータ鍵の扱い |

つまり **「何を満たすべきか」は文書化されているが、「満たしているかを確かめる共通の手段」がない**。

## 2. 作るもの

> 仕様上の要求を実行可能なテストに翻訳し、誰でも同じ条件で SAML 実装を検証できる OSS。

認定機関を名乗らない。**再現可能なテスト結果そのもの**を品質の証明として流通させる。

### なぜ Kantara Implementation Profile から始めるのか

- 実装者（software implementer）向けに書かれており、配備固有の事情に依存しない
- 全 <!--g1:requirements-->69<!--/g1--> 要件に `[IIP-xxNN]` の一意な識別子が振られている（テストとの 1:1 / 1:N 対応が作れる）
- ほぼ全てが MUST。「守っているか」の二値判定に適する
- Keycloak / Shibboleth / SimpleSAMLphp / Authentik といった実装が実際に参照している

## 3. 非目標（Non-goals）

| やらないこと | 理由 |
|---|---|
| 認定・認証（Certification）を発行する | 認定機関としての正統性がない。「Tested」までに留める |
| 「Certified」「Compliant」等の語を結果に使う | 誤認を招く。使用語は `Tested` / `Conformance Test Result` |
| SAML 1.x のサポート | 対象外 |
| 特定製品（Authrim 含む）専用のコード | Suite から見れば全ての実装が等しく外部実装 |
| SAML ライブラリ / IdP / SP 製品そのものを提供する | Test Peer は「テスト用の相手役」であり本番利用を想定しない |
| 性能・負荷試験 | スコープ外 |
| 対象システムへの侵入的な操作 | Phase 4 の Security Profile でも、あくまで対象との正規プロトコル経路のみを使う |

## 4. 既存ツールとの関係

調査した既存 OSS / サービス:

| 名前 | 何をするか | 本 Suite との差分 |
|---|---|---|
| [codice/saml-conformance](https://github.com/codice/saml-conformance) | SAML Core 仕様に対する IdP のブラックボックステスト。Kotlin/Java、CLI ベース | **IdP 専用**、SP テスト不可。Kantara IIP ではなく OASIS Core が対象。**LGPL-3.0** のためコード再利用は license 上の制約が大きい。Web UI・結果共有なし。事実上メンテナンスが停滞 |
| SAMLtest.id (Shibboleth) | 公開の試験用 IdP / SP。手で繋いで動作確認する | 「動くか」は分かるが、要件単位の合否レポートは出ない |
| samltool.com / SAMLTracer 等 | SAML メッセージのデコード・検証ユーティリティ | 単発の解析ツール。テストプラン・レポートの概念がない |
| [spid-sp-test](https://pypi.org/project/spid-sp-test/) / AgID spid-saml-check | イタリア SPID プロファイル専用の適合性チェッカ | **国内プロファイル専用**。構造は非常に参考になる（レポート JSON、CLI）。汎用 SAML には使えない |
| SAML Raider / WS-Attacker / EsPReSSO | SAML への攻撃テスト（Burp 拡張など） | 手動のペネトレーションテスト用。Phase 4 の参考。適合性レポートは出ない |

**本 Suite の独自性**は次の 4 点に集約される。

1. IdP と SP の**両方向**をテストする
2. **Requirement ID 単位**のレポート（仕様根拠まで追跡可能）
3. Web UI + Docker で、**専門知識がなくても Test Plan を作って実行できる**
4. 結果を**共有可能な形式**として流通させる

> 注: codice/saml-conformance は LGPL-3.0、**Samlier は Apache-2.0**。
> 設計・テスト観点の参照は自由だが、**コードのコピーは行わない**こと。
> `ctk/idp/NotTested.md`（外部から検証不能な要件の一覧）は考え方の参考として有用。

## 5. Phase 1 の成功条件

> 任意の SAML IdP/SP 実装者が Docker を起動し、Web UI から Kantara Implementation Profile v1.1 ベースの
> テストを実行し、各 Requirement の PASS/FAIL と根拠を確認でき、希望すればその結果を
> 公開 URL として第三者に提示できること。

これに、検証可能な受け入れ条件を足す。

### ★ 検出力は mutant peer で証明する

**「リファレンス実装 3 つで結果に差が出ること」を完了条件にしてはならない。**
差が出ないことは Suite の欠陥を意味しない — 3 製品とも適合している可能性も、
差が出ても設定の違いに過ぎない可能性もある。実製品は**オラクルにならない**。

代わりに、**既知の違反を注入した mutant Test IdP / Test SP** を用意し、
「狙った義務が必ず違反として検出されること」を golden test にする。

#### ★ 用語: mutant は「対象（SUT）」であって Suite の Test Peer ではない

Samlier の `peer/`（Test IdP / Test SP）は**検査する側**であり、常に正しく動く。
mutant は**検査される側（SUT: System Under Test）**に注入する。
`tests/mutants/` は「意図的に違反する対象実装」の定義であり、`peer/` とは別物。
混同すると「Suite 側を壊して検出力を測る」という誤った実装になる。

#### ★ オラクルは「絶対値」ではなく baseline からの差分

「正常な SUT では全義務が PASS」は**成立しない**。
役割違い（SP プロファイルでの `IIP-IDP*`）、条件付き義務、`CONFIG`、`ATTESTED` があるため、
単一の Run で全義務が PASS になることはない。
同様に「`reject-everything` ではどの義務も PASS してはならない」も**誤り**で、
拒否を要求する `MUST_NOT` 系は一律拒否でも満たせる。

そこで **baseline outcome vector** を先に取り、mutant はそこからの**差分**で判定する。

#### ★ baseline は 1 本では足りない — matrix にする

`role: sp` の baseline では **`IIP-IDP*` が全て `NOT_APPLICABLE`** になり、
IdP の mutant を検出できない。Core/Full、条件付き機能、`CONFIG` の設定差も覆えない。

```yaml
# tests/mutants/baselines.yaml
baselines:
  - id: sp-full-slo-enc
    role: sp                     # SUT の役割（Suite は Test IdP を演じる）
    profile: sp-full
    declared_features: { single_logout: true, assertion_encryption: true, ecp: false }
    config_fixture: tests/fixtures/sut/sp-full-slo-enc/    # ★ 設定差で結果が変わる
    interaction: { allow_browser_steps: true, allow_attestation: true }
  - id: sp-core-minimal
    role: sp
    profile: sp-core
    declared_features: { single_logout: false, assertion_encryption: false }
    config_fixture: tests/fixtures/sut/sp-core-minimal/
  - id: idp-full
    role: idp
    profile: idp-full
    declared_features: { ecp: true, assertion_encryption: true }
    config_fixture: tests/fixtures/sut/idp-full/
  - id: idp-core-no-ecp
    role: idp
    profile: idp-core
    declared_features: { ecp: false }
    config_fixture: tests/fixtures/sut/idp-core-no-ecp/
outcomes:                        # baseline ごとの期待 outcome（全 <!--g1:case_target-->380<!--/g1--> 義務）
  sp-full-slo-enc:
    IIP-SP13.a: satisfied
    IIP-SP13.b: satisfied
    IIP-IDP01.a: not_applicable  # 役割違い
    IIP-SP14.c: not_supported    # OPTIONAL の未実装申告
    ...
```

**期待値は `outcome` で書き、Verdict は書かない。**
`satisfied` / `violated` から `PASS` / `WARNING` / `NOT_SUPPORTED` への変換は
`Evaluator` が `level` を見て行う（[docs/05 §2.3](05-test-definition-format.md)）。
mutant 定義に `FAIL` と書くと、SHOULD 義務を一律 FAIL にする誤りが再発する。

```yaml
# tests/mutants/no-signature-validation.yaml
id: no-signature-validation
base: sp-full-slo-enc            # ★ どの baseline に対する mutant かを明示
injected_violation_ja: Response の XML 署名を一切検証しない
expected_changes:                # baseline から変わるべき義務（outcome で書く）
  IIP-SP13.a: violated
  IIP-MD07.b: violated
unchanged_required: all_others   # それ以外は baseline と一致すること
```

`unchanged_required: all_others` が要点で、これがないと
**「何でも violated にする Suite」が golden test を通ってしまう**。

#### control の失敗は対象の違反ではない

positive control（満たす実装が通ること）が失敗した場合、
それは**対象の規範違反ではなく Suite 側の問題**である。
`violated`（→ FAIL）にせず **`control_failed`** として扱い、
当該ケースは `NOT_VERIFIED(control_failed)` にする。
これを混同すると、Suite の不具合を対象の不適合として表示することになる。

### 初期の mutant セット

G2 で `tests/cases.yaml` の `detected_by_mutants` と対応づける。

| mutant | base | 注入する違反 | `expected_changes` |
|---|---|---|---|
| `no-signature-validation` | sp-full | 署名を検証しない | IIP-SP13.a / IIP-MD07.b |
| `first-key-only` | sp-full | 複数鍵の最初しか試さない | IIP-MD07.b / IIP-SP08.c |
| `first-key-only-idp` | idp-full | 同上（SLO の EncryptedID） | IIP-IDP19.c |
| `gcm128-only` | sp-full | AES128-GCM しか受け付けない | IIP-ALG04.b |
| `oaep-sha1-reject` | sp-full | DigestMethod sha1 を拒否する | IIP-ALG06.c |
| `crash-on-extension` | sp-full / idp-full | 未知の拡張要素で落ちる | IIP-EXT01.b |
| `crash-on-unknown-attribute` | sp-full / idp-full | 未知属性で落ちる | IIP-EXT01.c |
| `truncate-256` | sp-full / idp-full | 256 文字の値を切り詰める | IIP-G02.a |
| `ignore-force-authn` | idp-full | `ForceAuthn` を無視 | IIP-IDP06.a |
| `no-error-response` | idp-full | エラー時に Response を返さない | IIP-IDP05.a / IIP-SSO03.b |
| `single-acs-only` | idp-full | ACS が 1 つしか使えない | IIP-IDP12.a |
| `reject-everything` | 各 baseline | 全て拒否する | **positive control を持つ全ケースが変化する**こと。`MUST_NOT` 系は baseline のまま |
| `accept-everything` | 各 baseline | 全て受理する | **negative control を持つ全ケースが変化する**こと |

`reject-everything` / `accept-everything` は
**control の機能そのものを検証する対照 mutant** である。

**受け入れ条件**

- [ ] baseline matrix が **IdP / SP × Core / Full × 主要な条件付き機能**を覆う
- [ ] 各 baseline の outcome vector が固定され、2 回実行して一致する（再現性）
- [ ] 各 mutant が `base` を明示している
- [ ] 各 mutant で `expected_changes` の義務が**その通りに変化**する
- [ ] 各 mutant で **それ以外の義務が baseline と一致**する
- [ ] `reject-everything` で **positive control を持つ全ケース**が変化する
- [ ] `accept-everything` で **negative control を持つ全ケース**が変化する
- [ ] **全義務が 1 件以上の mutant で検出される、または `mutant_waiver` を持つ**
      （[G2 の通過条件](01-scope-and-roadmap.md)）
- [ ] control の失敗が `control_failed` として扱われ、対象の FAIL にならない
- [ ] Test Plan 作成から結果表示まで、利用者が触るドキュメントが `README` 1 枚で足りる
- [ ] 対象側で必要な設定作業が **メタデータ URL の登録 1 回 + オプション設定** に収まる
- [ ] 同じ Suite バージョン・同じ Test Plan で 2 回実行して結果が一致する
- [ ] 外部から検証不能な義務が `PASS` に混ざっていない（[03](03-test-model.md) の判定語彙）

**リファレンス実装（Keycloak / Shibboleth / SimpleSAMLphp）の位置づけ**は
「回帰検知と相互運用の確認」であり、**検出力の証明ではない**（[09 D-12](09-open-decisions.md)）。

## 6. Authrim との関係（明文化）

Authrim は本プロジェクトの発案元だが、Samlier のコードには **Authrim 固有の依存を一切入れない**。
Suite から見れば Authrim も Keycloak も等しく「外部の SAML 実装」である。

3 つの文脈で Authrim が登場しうるが、それぞれ別の話として扱う。

| 文脈 | 扱い |
|---|---|
| **テスト対象としての Authrim** | 他の実装と完全に同じ。優遇も特別扱いもしない。リファレンス実装の公開サンプルに含める場合は、Keycloak / Shibboleth / SimpleSAMLphp と同じ書式・同じ Test Plan 構成で並べる |
| **Hosted 版のログイン IdP としての Authrim**（将来） | 配備上の選択にすぎない。Samlier が実装するのは標準準拠の OIDC RP であり、設定で Keycloak にも Auth0 にも向けられること。→ [09 D-09](09-open-decisions.md) |
| **開発中の動作確認相手としての Authrim** | 自由に使ってよいが、Authrim でしか通らないテストを書かないこと。CI のリファレンス実装は Authrim 以外を必ず含める |

> 利益相反に見えないことが重要。README に上記を明記し、
> 公開する Authrim の結果は他の実装と同じ扱いにする。
