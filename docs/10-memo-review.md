# 10. 元構想メモのレビュー

対象: 「SAML Conformance Test Suite 構想まとめ」（2026-08-25 提示）

**総評**: 方向性・スコープの切り方・技術選定の骨格はいずれも妥当。
Phase 1 を Kantara Implementation Profile に絞る判断は特に良い。
一方で、**SAML をブラックボックスで外部テストすることの実務上の制約**が織り込まれておらず、
そのまま実装に入ると Phase 1 の途中で設計をやり直すことになる箇所が 8 点ある。

---

## A. 論理的な破綻・矛盾（要修正）

### A-1. ★ `NOT SUPPORTED` と `FAIL` を区別する前提が成り立たない

> メモ: 「実装されていない Optional 機能は FAIL とは区別し、NOT SUPPORTED のように結果を表現します」

Kantara IIP v1.1 の 69 要件のうち、**SHOULD / MAY / OPTIONAL は 6 件程度**で、
残りは全て MUST である。MUST 要件を「実装していない」と申告することは、
定義上そのプロファイルに適合していないということであり、FAIL と区別してはいけない。

→ 修正: RFC2119 レベルで機械的に決める。
MUST 未実装 = `FAIL(declared-unsupported)` / SHOULD 未実装 = `WARNING` / MAY 未実装 = `NOT_SUPPORTED`。
`NOT_APPLICABLE` は利用者の申告ではなく Profile と Test Plan 構成から決まるものに限定。
→ [03-test-model.md §4](03-test-model.md)

### A-2. 結果画面の「PASS 74 / FAIL 0 / N/A 8」の数字が要件数と合わない

IIP v1.1 の IdP プロファイルが評価する要件は **Common 31 + IdP 21 = 52 件**。
74 という数は「テストケース数」（1 要件 : N ケース）でないと出てこない。

→ 修正: レポートで **要件粒度とケース粒度の両方**を明示する。
「Requirements 52 / Test cases 74」。混ぜると読者が誤解する。
→ [06-results-and-publication.md §1](06-results-and-publication.md)

### A-3. ★ `docker run -p 8080:8080` → `http://localhost:8080` だけでは完走できない

> メモ: 「理想的には docker run -p 8080:8080 ... で http://localhost:8080 を開くだけ」

これはフロントチャネル（ブラウザ経由）のテストでしか成立しない。
IIP の要件のうち **メタデータ系（MD01〜MD12）と SOAP SLO / ECP は、
対象サーバから Suite に直接到達できないと実行できない**。
これは全要件の約 4 割にあたる。

→ 修正: `PUBLIC_BASE_URL` を必須概念にし、Preflight で到達性を判定して
「Local-only / Reachable / Hosted」の動作モードを明示する。
Test Plan 作成時に「この構成では N 件の要件が評価されません」と事前に警告する。
→ [07-deployment-and-networking.md §2](07-deployment-and-networking.md)

### A-4. ★ 共有 URL の信頼モデルが目的と矛盾する

> メモ: 「Test Run 終了後、`https://samltest.example/results/01K3...` のような URL を発行できる」
> メモ: 「再現可能なテスト結果そのものを品質証明にする」

self-hosted で誰でも Suite を動かせる設計なので、**結果 JSON は自由に捏造できる**。
捏造した結果に公開 URL を発行できるなら、「結果そのものが品質証明」という前提が崩れる。

→ 修正: 実行環境による信頼レベルを 3 段階に分け、公開ページに必ず表示する。
Phase 1 は「self-hosted = ローカルエクスポートのみ / Hosted 実行 = 共有 URL 発行可」を推奨。
→ [06-results-and-publication.md §3](06-results-and-publication.md)、決定事項 [D-04](09-open-decisions.md)

### A-5. SP を試験する図の矢印の順序が逆

> メモ:
> ```
> Test IdP → Response / Assertion → Target SP → AuthnRequest etc. → Test Suite
> ```

SP-initiated SSO では **Target SP が AuthnRequest を出すのが先**で、Test IdP が Response を返す。
IdP-initiated（unsolicited）なら Response が先で AuthnRequest はそもそも存在しない。
2 つのフローが混ざっている。

→ 修正: 2 つのフローを別図にする。この区別は実装上も重要（次項）。

### A-6. ★ 「Suite が反対側を演じる」だけでは SP のリクエスト生成系テストが識別できない

OIDF Conformance Suite はテストごとに新しい issuer / client を発行できるが、
**SAML には動的クライアント登録がない**。対象への登録は手作業になる。
テストケースごとに entityID を変えると、利用者は数十回の登録作業を強いられる。

さらに、SP が発行する AuthnRequest の宛先は SP が Test IdP メタデータから選ぶ 1 つの URL なので、
**URL でケースを識別できない**。

→ 修正:
- **1 Test Plan = 1 entityID = 1 つの「全部入りメタデータ」**（複数の鍵・ACS index・バインディングを最初から含める）
- ケース切替は、IdP テストでは `RelayState` / `InResponseTo` / ACS index、
  SP テストのレスポンス処理系では Suite 起点の unsolicited、
  SP テストのリクエスト生成系では**アーミング方式**（UI で「次の AuthnRequest をケース N とする」と宣言）
→ [02-architecture.md §3](02-architecture.md)

### A-7. テスト定義 YAML の `expected` が宣言的に書けるように見える

> メモ:
> ```yaml
> expected:
>   expired_metadata: reject
> ```

「reject とは何をもって reject と判定するか」を YAML で定義することはできない。
このまま進めると、YAML が実装の一部を中途半端に持ち、両方を見ないと意味が分からなくなる。

→ 修正: **YAML は「何を・なぜ・どの仕様を根拠に」について規範的、Java は「どう判定するか」について規範的**、
と役割を分離する。YAML の `expected` は人間向けの散文にし、判定ロジックは実装クラスに置く。
両者の 1:1 対応を CI で強制する。
→ [05-test-definition-format.md](05-test-definition-format.md)

### A-8. `IdP Basic / IdP Full` の区別基準が定義されていない

IIP 原文には Basic / Full の区別がない。何を基準に分けるかを決めないと、
「Basic に通った」の意味が説明できない。

→ 修正: **Core = MUST 要件のうち SSO / Metadata / Algorithms、Full = Core + SLO + ECP + SHOULD 以下**、
という基準を置く。**この分類が Suite 独自のものであることをレポートに明記する**。
→ [01-scope-and-roadmap.md](01-scope-and-roadmap.md)、[04-requirement-coverage.md](04-requirement-coverage.md)

---

## B. 重大な欠落（追記した項目）

| # | 欠落していた論点 | なぜ致命的か | 追記先 |
|---|---|---|---|
| B-1 ★ | **判定の観測可能性**。「対象が拒否したこと」は外から機械的に見えない場合が多い | これがないと negative test の大半が「何も起きなかった → PASS」になり、レポートが無意味になる | [03 §3, §5](03-test-model.md) |
| B-2 ★ | **対象側の設定変更を要するテストが約 4 割**（メタデータ再読込、属性リリース設定など） | 「メタデータ URL を 1 回登録すれば全部走る」は成立しない。UI に `WAITING_CONFIG` ステップが必要 | [03 §8](03-test-model.md), [04](04-requirement-coverage.md) |
| B-3 | **ブラウザが試験経路の一部**であること。利用者のログイン操作、SSO セッションの再利用、`ForceAuthn` / SLO の順序依存 | 順序を考えずに実装すると、テストが互いのセッションを壊して結果が不安定になる | [02 §3](02-architecture.md), [03 §9](03-test-model.md) |
| B-4 ★ | **Suite 自身のセキュリティ**。任意 URL に接続する道具なので Hosted 版は SSRF の踏み台になる。対象から来た XML をパースするので XXE の的にもなる | 公開サービスとして出す以上、後付けできない | [08](08-suite-security.md) |
| B-5 | **HTTPS / SameSite Cookie 制約**。多くの実装は `http://` の ACS を拒否し、HTTP-POST バインディングは `SameSite=None; Secure` を要求する | localhost の HTTP では実用にならない | [07 §3](07-deployment-and-networking.md) |
| B-6 | **時刻同期**。コンテナの時刻ズレは全テストを壊す | 原因不明の失敗として利用者を消耗させる | [07 §4](07-deployment-and-networking.md) |
| B-7 | **Test Plan の構成宣言を結果に刻む**（実装済み機能の申告、clock skew の解釈値など） | これがないと 2 つの結果を比較できず、「PASS 74」に意味がなくなる | [03 §2](03-test-model.md), [06 §1](06-results-and-publication.md) |
| B-8 | **公開結果への個人情報混入**。実 IdP でテストすると Assertion に実在ユーザーの氏名・メールが入る | 最も起きやすい事故。既定マスクが必須 | [06 §4](06-results-and-publication.md) |
| B-9 | **既存 OSS との関係**。codice/saml-conformance（LGPL-3.0、Kotlin、IdP 専用、OASIS Core 対象） | ライセンス上コードを流用できない。差別化の説明も必要 | [00 §4](00-concept.md) |
| B-10 | **仕様原文の引用範囲**（Kantara 文書の IPR） | 要件テキストを全文転載すると権利上の問題になりうる | [09 D-11](09-open-decisions.md) |
| B-11 | **Preflight チェック** | これがないと利用者が原因不明の失敗に遭う | [03 §10](03-test-model.md) |
| B-12 | **結果の再現性**。Suite バージョンだけでなくテスト定義のダイジェストも必要 | 同じバージョンでも定義が変われば結果が変わる | [06 §1](06-results-and-publication.md) |
| B-13 | **ECP プロファイル**（IIP-IDP13〜16）。IIP では MUST | メモに言及がない。しかも **ECP はバックチャネルのみで完全自動化できる**数少ない領域で、実装価値が高い | [04](04-requirement-coverage.md) |

---

## C. 事実確認と修正

| 項目 | メモの記述 | 確認結果 |
|---|---|---|
| Kantara IIP のバージョン | 指定なし | **v1.1 (2019-12-18)** が最新の Kantara Recommendation。Phase 1 の対象として明記した |
| 要件 ID | `IIP-G01` `IIP-G03` `IIP-MD03` `IIP-MD04` `IIP-MD07` `IIP-SSO01` `IIP-SSO02` `IIP-IDP08` | **全て実在し、メモの説明も正しい**（Clock skew / DTD rejection / Metadata signature / Metadata expiration / Multiple signing keys / Browser SSO / Redirect-POST / RequestedAuthnContext）。原文に忠実 |
| 要件の総数 | 記載なし | Common 31 / SP 17 / IdP 21 = 69 |
| Phase 3 の「SAML2Int」 | 「SAML2Int / Deployment Profile」 | saml2int.org (v0.2.1) は歴史的文書。現在は **Kantara SAML V2.0 Deployment Profile for Federation Interoperability v2.0** が後継。参照先を差し替えた |
| Java 21 + OpenSAML | 第一候補 | **OpenSAML 5.x は Java 17+、Apache-2.0。Java 21 で問題なし。妥当** |
| Apache Santuario | XML Security に使う | 妥当。加えて **不正署名を作るには OpenSAML の Signer を迂回して Santuario を直接叩く必要がある**点を設計に追記 |
| 「OpenSAML だけに依存しない」 | Phase 4 のために低レベル XML 操作も残す | **正しい判断**。ただし Phase 4 で後付けすると生成経路が二重化して破綻するため、**Phase 1 でインターフェースだけ切る**ことを追記 |
| Docker 一本化 | Cloudflare Workers 版を作らない | **正しい**。XML Security の実装が JVM 前提であり、Workers での再実装は現実的でない |
| SQLite | 第一候補 | Phase 1 では妥当。Transcript のような大きなデータは DB でなくファイルに置き、DB アクセス層を薄く保って PostgreSQL への差し替え余地を残す旨を追記 |

---

## D. そのまま維持した良い判断

- **Phase 1 を Kantara IIP に絞る**。要件 ID が振られており、テストとの対応が作りやすい
- **認定機関を名乗らず、「Tested」に留める**。用語規約として明文化した（[06 §3](06-results-and-publication.md)）
- **Authrim 固有コードを入れない独立 OSS にする**。Suite から見て Authrim も Keycloak も等しく外部実装
- **Hosted 版と self-hosted 版で同じイメージを使う**。判定ロジックの分岐を作らない
- **テストの意味をコードから分離する**（方式は修正したが意図は正しい）
- **Docker 一本化**
- **Phase 4 の Security Profile を最初から視野に入れる**（ただし作らない）

---

## E. 追加した設計上の提案

| # | 提案 | 効果 |
|---|---|---|
| E-1 | **1 Test Plan = 1 entityID の「全部入りメタデータ」** | 対象への登録作業が 1 回で済む。採用可否を左右する最大の実務要素 |
| E-2 | **アーミング方式**（SP のリクエスト生成系テスト） | SAML に動的登録がない制約を回避する唯一の現実的な方法 |
| E-3 | **証拠ラダー L1〜L4 + `INDETERMINATE` を不合格扱い** | 「何も起きなかった → PASS」を構造的に防ぐ。Suite の信頼性の核 |
| E-4 | **IIP-IDP05（エラー Response を返す）を最優先で実行** | この要件を満たす IdP ほど他の negative test を自動判定できる。検出力を最大化する実行順序 |
| E-5 | **クイック実行モード**（対象側の設定変更を要するテストを飛ばす） | 初回体験を 10 分に収める。採用のハードルを下げる |
| E-6 | **Preflight チェック** | 原因不明の失敗をなくす |
| E-7 | **カバレッジ表を機械可読にして Markdown を生成** | 手書き表は必ず実態とずれる |
| E-8 | **Phase 1 の成功条件に「3 実装で結果に差が出ること」を追加** | 全部 PASS のレポートは検出力を証明しない。Suite が「効いている」ことの受け入れ条件 |
| E-9 | **「reasonable」の数値解釈を Test Plan パラメータにして結果に刻む** | 仕様に数値がない要件（IIP-G01 等）で Suite の独断を避ける |
| E-10 | **UI と Test Peer エンドポイントを別オリジンに分ける検討** | 対象由来のコンテンツが Suite の UI セッションに触れない |
