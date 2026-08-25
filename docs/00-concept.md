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
- 全 69 要件に `[IIP-xxNN]` の一意な識別子が振られている（テストとの 1:1 / 1:N 対応が作れる）
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

これに、元メモに欠けていた検証可能な受け入れ条件を足す。

- [ ] Keycloak / Shibboleth IdP / SimpleSAMLphp の 3 実装に対して IdP Profile を通し、**結果に差が出る**こと（全て PASS では検出力を証明できない）
- [ ] 同上で SP Profile を通せること
- [ ] Test Plan 作成から結果表示まで、利用者が触るドキュメントが `README` 1 枚で足りること
- [ ] 対象側で必要な設定作業が **メタデータ URL の登録 1 回 + オプション設定** に収まること（[02](02-architecture.md) 参照）
- [ ] 同じ Suite バージョン・同じ Test Plan で 2 回実行して結果が一致すること（再現性）
- [ ] 外部から検証不能な要件が「PASS」に混ざっていないこと（[03](03-test-model.md) の判定語彙）

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
