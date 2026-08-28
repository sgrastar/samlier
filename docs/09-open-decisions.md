# 09. 意思決定ログ

`✅` = 決定済み / `⏸` = 保留 / `★` = 先に決める必要があるもの

**残っている未決事項は D-15（Hosted 版の運用）のみ。** 実装は M0〜M3 の間これに依存しない。

---

## ✅ D-01. プロジェクト名 / リポジトリ名 — **決定: Samlier**

| 項目 | 値 |
|---|---|
| プロダクト名 | **Samlier** |
| タグライン | `Samlier — SAML Conformance Test Suite` |
| リポジトリ | **`github.com/sgrastar/samlier`**（当面は個人アカウント配下） |
| Java パッケージ | `org.samlier.*` |
| Docker イメージ | `samlier/suite` |
| 環境変数プレフィックス | `SAMLIER_` |

固有名のため商標・既存プロジェクトとの衝突リスクが低い。機能はタグラインで補う。
（不採用: `samlconf` = 検索性が低い、`saml-conformance-suite` = codice/saml-conformance と紛らわしい、
`samltest` = Shibboleth の SAMLtest.id と衝突）

### 名前空間の空き状況（2026-08-25 確認）

| 名前空間 | 状態 | 備考 |
|---|---|---|
| GitHub repo `sgrastar/samlier` | ✅ 空き | ここで開始する |
| GitHub org/user **`samlier`** | ❌ **取得済み** | 個人ユーザー（uid 91261879、public repo 0、bio なし＝実質休眠）。GitHub の休眠名開放は期待できない |
| Docker Hub **`samlier`** | ✅ 空き | `samlier/suite` が使える。**無料アカウントで今すぐ確保推奨** |
| npm `samlier` | ✅ 空き | 現状は使う予定なし |
| PyPI `samlier` | ✅ 空き | 同上 |
| `samlier.com` | ❌ 登録済み | Dominet (HK) Limited 経由。HTTP 403 を返すのみ＝パーク/転売用とみられる |
| `samlier.org` | ✅ 未登録 | PIR の RDAP で 404 確認 |
| `samlier.dev` | ✅ 未登録 | |
| `samlier.io` | ✅ 未登録 | |
| `samlier.net` / `samlier.app` | ✅ 未登録 | |

**GitHub org が取れないことへの対応**: 当面 `sgrastar/samlier` で問題ない。
コミュニティが育った時点で `samlier-project` などの org を作って transfer する。
GitHub は transfer 後も旧 URL をリダイレクトするため、移行コストは低い。

> **今すぐやること**: Docker Hub の `samlier` 名前空間を確保する（空いているうちに）。

## ✅ D-02. ライセンス — **決定: Apache-2.0**

OpenSAML / Santuario と同じ。特許条項があり ID 基盤系 OSS の事実上の標準。
ベンダーが自社 CI に組み込みやすく、採用が広がる。

**制約**: [codice/saml-conformance](https://github.com/codice/saml-conformance) は **LGPL-3.0**。
設計・テスト観点の参照は自由だが、**コードのコピーは行わない**。
`ctk/idp/NotTested.md`（外部から検証不能な要件の一覧）は考え方の参考として読むに留める。

貢献者の扱い: DCO (`Signed-off-by`) を採用し、CLA は課さない（貢献の敷居を下げる）。

## ✅ D-03. Phase 1 の最初のリリース（v0.1）— **決定: C = Phase 1 完全**

v0.1 = IIP v1.1 の全要件（Common 31 + SP 17 + IdP 21）を対象とし、
**Single Logout と ECP も含める**。

> **含意**: 初リリースまでが長くなる。内部マイルストーンを切って進捗を可視化する。
> → [01-scope-and-roadmap.md](01-scope-and-roadmap.md) のマイルストーン節を参照。
>
> ただし UI 上の **「クイック実行」モード**（対象側の再設定を要するテストを飛ばし、
> 10 分で SSO コアだけ確認する）は v0.1 で必須とする。
> スコープを完全にしても、初回体験が 1 時間かかる設計にはしない。

## ✅ D-04. 結果公開の信頼モデル — **決定: Level 0 + Level 2**

| レベル | 実装 |
|---|---|
| Level 0 — LOCAL | ✅ self-hosted の結果は `result.json` / `report.html` のローカルエクスポートのみ |
| Level 1 — ATTESTED UPLOAD | ❌ **不採用**。捏造 JSON をアップロードできてしまい、結果全体の価値が下がる |
| Level 2 — HOSTED RUN | ✅ 公式 Hosted 版で実行した結果のみ共有 URL を発行。Transcript を Suite 側が保持する |

`SAMLIER_PUBLISH_ENABLED` は `hosted` モードでのみ `true` になり、
self-hosted ビルドで有効化しても**公式の結果ドメインには載らない**。

> **含意**: Phase 1 で Hosted 版の運用が必要になる。
> ドメイン、ホスティング先、認証、削除要請の窓口を決める必要がある（D-09、D-15）。

---

## ✅ D-05. Web フレームワーク — **決定: Javalin + Jetty**

**技術的制約**: SAML の HTTP-Redirect バインディングの署名検証には
**URL デコード前の生のクエリ文字列**が必要（`SAMLRequest=...&RelayState=...&SigAlg=...` の
バイト列そのものが署名対象）。パラメータをパースして再構成すると検証が壊れる。
HTTP-POST も同様に生の base64 が必要。→ [02 §3.5](02-architecture.md)

Javalin を選ぶ理由:
- 依存が小さくイメージが軽い。生リクエスト（`ctx.req().getQueryString()` / 生ボディ）へのアクセスが素直
- SSE を標準でサポート（Test Run の進捗配信）
- フレームワークのコードが薄く、読み手の注意が SAML 処理本体に向く

自前で用意することになるもの（Spring Boot なら付いてくる分）:

| 機能 | 方針 |
|---|---|
| 設定 | 環境変数 + 単純な設定クラス。設定ライブラリは入れない |
| DI | コンストラクタ注入を手書き。DI コンテナは入れない |
| スケジューラ | `ScheduledExecutorService`（保持期間の削除、メタデータ定期取得） |
| バリデーション | 手書き。API のスキーマは小さい |
| マイグレーション | 素の SQL ファイル + バージョン管理テーブルを自前で |

> **フィルタ / プロキシによる URL 正規化の禁止**を実装初期にテストで固定すること。
> `%2F` を `/` に戻すような正規化が入ると、署名検証の誤判定を配るスイートになる。

## ✅ D-06. フロントエンド — **決定: React + Vite**

エコシステムが最大で、XML / コードビューア・差分表示・仮想スクロール（大きな結果ツリー）の
ライブラリが揃う。貢献者も見つけやすい。

Phase 1 の UI の主要素:
- Test Plan 作成フォーム（構成宣言・`declared_features`・`parameters`）
- Test Run の進捗（SSE。`WAITING_BROWSER` / `WAITING_CONFIG` / `WAITING_ATTEST` の対話 UI）
- 結果ツリー（要件 → ケース → 判定理由 → Transcript → 生 XML）
- XML ビューア（整形・ハイライト・署名対象要素のハイライト）
- 公開前プレビュー（マスク結果の確認）

`report.html`（自己完結の単一 HTML）は、**同じ React アプリを結果 JSON 埋め込みで
静的ビルドしたもの**にする。別実装を作らない。

## ✅ D-07. ビルドツール — **決定: Gradle (Kotlin DSL)**

マルチモジュールと npm ビルドの連携（`com.github.node-gradle`）が素直で、
`report.html` の静的ビルドのようなタスク依存を表現しやすい。インクリメンタルビルドも速い。

ビルドが担うもの:

```
:core :saml :peer :runner :tests :api      Java 21 マルチモジュール
:tests:defs   → YAML を resources に埋め込み + 整合性チェック（05 §5）を check に組み込む
:web          → Vite ビルド。成果物を :api の resources に配置
:web:report   → 結果 JSON 埋め込み用の単一 HTML ビルド（同じ React アプリ）
:dist         → Docker イメージ（jib もしくは Dockerfile）。amd64 / arm64
```

`./gradlew check` に **テスト定義の整合性チェックを必ず含める**（YAML と実装の 1:1 対応、
カバレッジ表との突き合わせ）。CI 専用スクリプトにするとローカルで壊れたまま PR が飛ぶ。

## ✅ D-08. リポジトリ構成 — **決定: 単一リポジトリ**

`github.com/sgrastar/samlier` に backend / frontend / テスト定義を全て置く。

テスト定義を別リポジトリに分けると、[05 §5](05-test-definition-format.md) で設計した
**「YAML と実装クラスの 1:1 対応を CI で強制する」**が成立しなくなる。
仕様の解釈変更はテスト定義とロジックの両方に及ぶため、同じ PR で変更できることに価値がある。

将来、テスト定義だけを他プロジェクトが再利用したくなった場合は、
`tests/defs/**` と `tests/coverage.yaml` を**リリース成果物として別途配布**する
（リポジトリを分けるのではなく、成果物を分ける）。

## ✅ D-09. Hosted 版の認証 — **決定: 認証なし + シークレット URL（将来 Authrim による OIDC/SAML ログイン）**

### Phase 1

Run 作成時に管理用のシークレット URL を 1 回だけ表示する。公開・削除はそれで行う。
利用の敷居が最も低い。ボット対策はレート制限（IP 単位）+ Turnstile 等で担保する。

#### ★ シークレットの扱い（レビュー指摘 11 を反映）

**クエリパラメータに置かない。** クエリはブラウザ履歴・アクセスログ・
同一オリジンからの `Referer`・スクリーンショット・共有された URL に残る。

```
公開 ID と管理トークンを完全に分離する:

  Result URL   https://samlier.example/results/01K3ZQ8N…        （公開 ID のみ）
  Manage URL   https://samlier.example/manage/01K3ZQ8N…#t=<token>
                                                     ^^^^^^^^^^
                                                     fragment（サーバに送られない）
```

| 対策 | 内容 |
|---|---|
| ID とトークンの分離 | `runId` は公開してよい識別子。管理トークンは無関係な高エントロピー値（128bit 以上） |
| 保存形式 | **トークンはハッシュ化して保存**（`SHA-256`）。DB 流出でトークンが使えないようにする |
| 受け渡し | 初回 URL の **fragment** で受け取り、JS が `POST /api/manage/session` で **HttpOnly + Secure + SameSite=Strict の Cookie に交換**する |
| ★ 履歴からの除去 | fragment はサーバには送られないが **ブラウザ履歴・ブックマーク・タブ復元には残る**。JS は値を読んだ**直後、ネットワーク処理より前**に `history.replaceState(null, "", location.pathname)` で fragment を消す。交換の成否にかかわらず実行する |
| ★ CSP | 管理画面に厳格な CSP。`script-src 'self' 'nonce-{レスポンスごとの乱数}'` を明示し、`'unsafe-inline'` / `'unsafe-eval'` / `'strict-dynamic'` を使わない。完全な例は [08 §5](08-suite-security.md) |
| ★ オリジン分離 | Hosted では `app.<domain>` と `peer.<domain>` の分離が **MUST**（[08 §5](08-suite-security.md)）。同一オリジンなら起動を拒否する |
| ★ Origin 検証 | `POST /api/manage/session` は `Origin` ヘッダを検証し、`app.<domain>` 以外を拒否する |
| `Referrer-Policy` | 管理画面・結果ページとも `no-referrer` |
| 外部リソース | 管理画面に外部由来のリソース（画像・スクリプト・iframe）を置かない |
| CSRF | `publish` / `delete` / `unpublish` は Cookie セッション + CSRF トークン。`SameSite=Strict` だけに頼らない |
| ローテーション | 管理画面からトークンを再発行できる。旧トークンは即時失効 |
| 失効 | Run 削除・保持期間経過でトークンも削除 |
| ログ | アクセスログに fragment は入らないが、**トークンがクエリで来た場合は即座に 400 を返して記録しない**（誤用の検出） |
| ブルートフォース | `runId` 単位のレート制限。トークン照合は定数時間比較 |

> `peer.<domain>` と `app.<domain>` を分けている（[08 §5](08-suite-security.md)）ため、
> Test Peer に届いた対象由来のコンテンツから管理 Cookie には触れられない。

### 将来: Authrim をログイン IdP として使う

Hosted 版のログインを Authrim（OIDC または SAML）で行う。
**ここには設計上の注意が 2 つある。**

**注意 1 — コードに Authrim 固有の依存を入れない。**
[00-concept.md](00-concept.md) の非目標「Authrim 固有コードを一切入れない」は維持する。
Samlier が実装するのは**標準準拠の OIDC RP（または SAML SP）** であり、
Authrim はその配備上の選択にすぎない。設定で Keycloak にも Auth0 にも向けられること。
Authrim 専用のエンドポイントや独自クレームに依存したら原則違反になる。

**注意 2 — ★ ログイン用の SP と Test Peer を完全に分離する。**
Samlier が SAML SP としてログインする場合、**同じプロセス内に
「テスト用の Test SP」と「ログイン用の SP」が同居する**ことになる。これは危険。

| | Test Peer (`/p/{plan}/sp/...`) | Login SP (`/auth/...`) |
|---|---|---|
| entityID | Test Plan ごとに発行 | Samlier 固定の 1 つ |
| 鍵 | Test Plan ごとに生成、`/data` に平文 | 運用鍵。**分離して管理** |
| 署名検証 | **意図的に緩い**（不正な署名を「受け取って観測する」のが仕事） | 厳格。通常の SP と同じ |
| セッション | テスト用の使い捨て | Samlier の管理権限を持つ |
| Assertion の受理 | 攻撃的な Assertion も受理して記録する | 通常の検証を全て通す |

**Test Peer の検証は意図的に緩い**ため、そこに来た Assertion で Samlier のログインセッションが
作られると認証バイパスになる。次を守る。

- 別のコードパス・別のセッションストア・別の Cookie 名にする
- **別オリジンに分ける**（`app.samlier.example` と `peer.samlier.example`）。
  [08 §5](08-suite-security.md) の方針と一致する
- Login SP は OIDC を第一候補にする（SAML を使うと同居の混乱が増える）
- シークレット URL 方式は OIDC ログイン導入後も残す（移行パス・匿名利用の維持）

> 副次的な利点: Samlier のログインを Authrim の OIDC/SAML で行えば、
> Authrim 側の実装のドッグフーディングになる。ただし **Samlier のテスト対象としての Authrim とは
> 完全に別の話**であることを README で明示する（利益相反に見えないようにする）。

## ✅ D-10. 要件カタログの機械可読化 — **決定・実施済み: `tests/coverage.yaml` を正とする**

[04-requirement-coverage.md](04-requirement-coverage.md) は **`tests/coverage.yaml` からの生成物**であり、手で編集しない。
**G1 作成フェーズで実施済み**（`tools/g1_build.py`）。

スキーマは **義務単位**（[05 §2.1](05-test-definition-format.md) が正）。
`level` / `testability` / `level_assignment` はいずれも義務に付く。

```yaml
# tests/coverage.yaml
spec: kantara-fedinterop-impl
version: "1.1"
requirements:
  - id: IIP-MD04
    section: "2.2.1"
    anchor: IIP-MD04
    obligations:
      - key: IIP-MD04.a
        roles: [idp, sp]
        level: MUST
        condition: null
        summary_en: "Reject metadata whose root element lacks a validUntil attribute"
        testability: CONFIG
        level_assignment: { idp: core, sp: core }
      - key: IIP-MD04.b
        roles: [idp, sp]
        level: MUST
        summary_en: "Reject metadata whose validUntil is in the past"
        testability: CONFIG
        level_assignment: { idp: core, sp: core }
      - key: IIP-MD04.c
        roles: [idp, sp]
        level: MUST
        summary_en: >
          Reject metadata whose validUntil is further into the future than the
          implementation's configured limit; that limit must be configurable
        testability: CONFIG
        level_assignment: { idp: core, sp: core }
```

> **訂正**: 前版のスキーマ例は要件単位（`level: [MUST]` / `applies_to`）のままだった。
> D-03 の Obligation 層の決定と矛盾していたので差し替えた（レビュー指摘 12）。

これを正として:
- `04-requirement-coverage.md` を生成する（`./gradlew :docs:generateCoverage`）
- CI で「`phase1: full` の全要件に 1 件以上のテストケースがある」を検証する
- UI の結果画面が要件のメタデータ（level / section / summary）をここから引く

> 手書きの Markdown 表は必ず実態とずれる。<!--g1:requirements-->69<!--/g1--> 要件 × 5 列を人力で保守しない。

## ✅ D-11. 仕様原文の引用範囲 — **決定: ID + 自作要約 + 原文アンカーへのリンク**

Kantara 文書の要件テキストは**転載しない**。テスト定義と結果画面に載せるのは:

1. 要件 ID（`IIP-MD04`）
2. **自分で書いた**短い要約（`spec.quote_summary` / `summary_en` / `summary_ja`）
3. 原文の該当アンカーへのリンク
   （`https://kantarainitiative.github.io/SAMLprofiles/fedinterop.html#IIP-MD04`）
4. 文書名・版・発行日・発行元の明示

法的に最も安全で、今すぐ進められる。

**並行して行うこと**: Kantara Initiative に要件原文の埋め込み可否を照会する。
許諾が得られれば、`spec` ブロックに `quote_full` を足すだけで切り替えられる設計にしておく
（要約フィールドは残し、原文はオプショナルな追加とする）。

**既知の欠点**: オフライン環境や隔離ネットワークでは根拠の原文を読めない。
`report.html` にも要約とリンクのみが入る。照会が通るまではこの制約を受け入れる。

## ✅ D-12. リファレンス実装の結果公開 — **決定: バージョン固定のサンプルとして公開**

| 用途 | 方針 |
|---|---|
| Samlier 自身の回帰検知 | Keycloak / Shibboleth IdP / SimpleSAMLphp を CI（GitHub Actions）で定期実行し、**結果の変化を内部で検知**する |
| ★ 検出力の証明 | **リファレンス実装ではなく mutant peer で行う**（[00 §5](00-concept.md)）。「3 製品で差が出ること」は完了条件から外した |
| 外部への提示 | **バージョンを固定したサンプル結果**を公開する。「このレポートはこう見える」という見本であり、常時更新はしない |

公開サンプルに必ず添えるもの:

- 対象のバージョンとその取得日（`Keycloak 26.0.5, tested 2026-09-01`）
- 「特定バージョンに対する一時点の測定であり、現在の状態を示すものではない」旨
- 使用した Test Plan の構成（`declared_features` / `parameters`）
- 「これは認定ではない」の定型文（[06 §3](06-results-and-publication.md)）

> 毎晩の結果を常時公開すると、他社製品の FAIL を継続的に晒すことになり、
> 「認定機関を名乗らない」という方針と緊張する。誤判定時の負債も大きい。
> **CI は回すが公開は固定サンプルに留める**のがこの緊張の解になる。

実装側へのフィードバックは、公開レポートではなく **各プロジェクトへの issue / PR** で行う。

### ★ リファレンス実装は検出力のオラクルではない

「3 製品で結果に差が出ること」を完了条件にしていたが**撤回した**。
差が出ないことは Suite の欠陥を意味しない（全て適合している可能性も、
差が出ても設定の違いに過ぎない可能性もある）。
**検出力の証明は mutant peer で行う**（[00 §5](00-concept.md)）。
リファレンス実装の位置づけは**回帰検知と相互運用の確認**である。

### ★ CI で回せる範囲（ブラウザ自動化との矛盾の解消）

[01](01-scope-and-roadmap.md) は Phase 1 でブラウザ自動化を除外しているが、
<!--g1:case_target-->541<!--/g1--> 義務のうち **`BROWSER` が <!--g1:tb_browser-->216<!--/g1--> 件**あるため **Full Profile は無人 CI で回せない**。
矛盾を残さないよう範囲を分ける。

| 用途 | 範囲 | ブラウザ |
|---|---|---|
| **CI（PR ごと / 定期）** | `AUTOMATED` の <!--g1:tb_automated-->96<!--/g1--> 義務 + **mutant peer の golden test** | 不要 |
| **リファレンス実装の定期実行** | `AUTOMATED` subset のみ | 不要 |
| **Full Profile** | 全 <!--g1:case_target-->541<!--/g1--> 義務 | **必要**。手動実行し、固定サンプルとして公開する |

**決定: Phase 1 では Playwright 等のブラウザ自動化を導入しない。**
CI は `AUTOMATED` subset と mutant golden test に限定する。
（Phase 2 でブラウザ自動化を入れれば CI の範囲を広げられる）

### リファレンス実装の固定（M4 までに作る）

```yaml
# tests/reference-impls.yaml
- id: keycloak
  roles: [idp, sp]                              # ★ 役割別マトリクス
  image: quay.io/keycloak/keycloak@sha256:…     # ★ digest 固定（タグは動く）
  config_fixture: tests/fixtures/keycloak/
- id: shibboleth-idp
  roles: [idp]
  image: "…@sha256:…"
- id: simplesamlphp
  roles: [idp, sp]
  image: "…@sha256:…"
```

image を digest で固定し、設定 fixture もリポジトリに置く。
環境差で結果が変わると回帰検知として機能しない。

## ✅ D-13. 多言語対応 — **決定: 英語のみ（`ja` の枠だけ用意）**

Phase 1 の UI・レポートは英語のみ。テスト定義 YAML には
`title_ja` / `instructions.ja` / `expected.ja` / `attestation.question_ja` の
**キーを定義しておく**が、CI の必須チェックは `en` のみにする。

```
CI 必須:  title, instructions.en, expected.en, (attestation.question_en)
CI 任意:  title_ja, instructions.ja, expected.ja, attestation.question_ja
```

こうしておけば、あとから日本語を埋めてもスキーマ変更も CI ルール変更も要らない。
UI 側も i18n の仕組み（キー参照）だけ最初から通しておき、辞書は `en` のみ同梱する。

> 日本語版を出す判断は、利用者が実際に付いてからでよい。
> ただし**あとから入れられる形にしておくコスト**はほぼゼロなので、枠だけ作る。

## ✅ D-14. 「reasonable」の数値解釈 — **決定: 中間値を既定とし、Test Plan で変更可能にする**

仕様に数値がない要件について、Samlier が採用する既定値。

| 要件 | 既定値 | 判定 |
|---|---|---|
| **IIP-G01** クロックスキュー | `clock_skew_tolerance_seconds: 180` | ±180 秒のずれを**拒否したら FAIL**（許容すべき下限）。**上限は判定しない**（advisory に分離。下記） |
| **IIP-MD04.c** validUntil が too distant | ★ **Samlier 側で閾値を決めない**（下記） | 対象側の設定閾値の**境界値**で判定 |

> **クロックスキューの上限も Samlier は決めない。** 原文（IIP-G01）は
> 「合理的なスキューを許容できること」を要求するだけで、
> **許容しすぎた場合の不適合条件を定めていない**。
> 極端に大きなスキューを受理したことは
> [04 §Advisory](04-requirement-coverage.md) の `clock_skew.very_permissive` として
> **判定に影響しない情報**として記録する（前版は WARNING にしていた。原文に根拠がない）。

#### ★ IIP-MD04.c は Samlier が閾値を決めてはいけない（レビュー指摘 5）

原文の MUST は *reject metadata if `validUntil` is too far into the future (**configurable**)* であり、
**閾値が設定可能であること**と**その設定に基づいて拒否できること**が義務である。
「90 日超を受け入れたら FAIL」という Samlier 独自の絶対閾値は原文より厳しく、
閾値を 365 日に設定している製品を誤って FAIL にする。

正しい手順:

```
① 利用者に、対象製品の「validUntil 上限」を任意の値 T に設定してもらう（WAITING_CONFIG）
   → できない場合は [03 §4 の共通判定手順](03-test-model.md)に従う。
      `configuration_failure_semantics: normative_capability`
      （閾値が設定可能であること自体が義務に含まれるため）。
      ケースは outcome を返し、Verdict への変換は Evaluator が level を見て行う
② Suite が validUntil = now + T − δ のメタデータを配布  → 受理されるべき
③ Suite が validUntil = now + T + δ のメタデータを配布  → 拒否されるべき
   δ は Test Plan の metadata_boundary_delta_hours（既定 24h）
④ 採用した T と δ を結果 JSON に記録する
```

**Samlier の都合で FAIL にしない。**
「利用者が答えられない」ことを製品の不適合にしない（共通判定手順の 3 分岐に従う）。

根拠（クロックスキューのみ）:
- **180 秒**は Shibboleth / SimpleSAMLphp の既定クロックスキューと一致し、説明しやすい

3 つの安全弁を設ける。

1. **全て Test Plan で変更できる**（`parameters`）
2. **採用値を結果 JSON に必ず記録する**（[06 §1](06-results-and-publication.md) の `configuration`）
3. **レポートに「これは Samlier の解釈であり仕様の規定ではない」と明記する**

> より厳しい値（±60 秒 / 7 日）はセキュリティ的には望ましいが、既存実装の実態より厳しく、
> FAIL が多発してレポート全体の信頼を損なう。
> より緩い値（判定しない）にすると IIP-G01 / MD04 が事実上未テストになる。
> 中間値 + パラメータ化 + 記録が、この緊張の解になる。

## ⏸ D-15. Hosted 版の運用 — **保留**（M4 着手前までに決める）

Level 2 を採用したため、公式 Hosted 版が Phase 1 の成果物に含まれる。

### ドメイン（要決定）

空き状況は [D-01](#-d-01-プロジェクト名--リポジトリ名--決定-samlier) の表の通り。
`.com` は押さえられているが、他は全て空いている。

| 候補 | 評価 |
|---|---|
| **`samlier.org`**（推奨） | OSS プロジェクトの慣行に沿う。OIDF が `openid.net` であるように、適合性テストの提供元として非営利的な印象が要る。安価で安定 |
| `samlier.dev` | Google Registry。**HSTS preload が強制され、HTTP でのアクセスが不可能**。Samlier は HTTPS 必須（[07 §3](07-deployment-and-networking.md)）なので方針と一致する。ドキュメントサイト用に併せて取るのは有効 |
| `samlier.io` | テック寄りだが高価で、`.io` TLD 自体の将来に不確実性がある（英領インド洋地域の主権移管に伴う議論）。**避けたほうが無難** |
| `samlier.com` | 取得済み。交渉・購入コストが読めない。追わない |

推奨: **`samlier.org` を本体として取得**し、`samlier.dev` をドキュメント用／防御的に併せて確保する。

### サブドメイン構成

[09 D-09](#-d-09-hosted-版の認証--決定-認証なし--シークレット-url将来-authrim-による-oidcsaml-ログイン) と
[08 §5](08-suite-security.md) の別オリジン方針に対応させる。

```
samlier.org           プロジェクトサイト / ドキュメント
app.samlier.org       Hosted 版の UI・管理（将来の OIDC ログインもここ）
peer.samlier.org      Test Peer のエンドポイント（対象から到達される面）★ 別オリジン
results.samlier.org   公開された結果（静的配信でよい）
```

`peer` を分ける理由: Test Peer は不正な Assertion も受け取って観測する設計であり、
そこに来たコンテンツが管理 UI のセッションに触れてはならない。

### その他の決定事項

| 項目 | 選択肢 |
|---|---|
| ホスティング | VPS + Docker / Fly.io / Cloud Run / 自宅サーバ + Cloudflare Tunnel |
| 費用負担 | 個人負担 / GitHub Sponsors / Open Collective |
| 運用ポリシー | 利用規約、削除要請の窓口、保持期間（[06 §5](06-results-and-publication.md)） |

> Hosted 版はインターネットから到達できる IdP/SP しかテストできない。
> **社内 IdP のテストは self-hosted が必須**であり、その結果は共有 URL にできない。
> この非対称性は README で明確に説明する必要がある
> （「社内システムをテストしたい」層と「結果を公開したい」層は別）。
