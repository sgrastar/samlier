# 08. Suite 自身のセキュリティ

この Suite は本質的に **「任意の URL に任意の HTTP リクエストを送り、任意の XML を生成する道具」**である。
Hosted 版として公開する以上、Suite 自身が攻撃基盤になりうる点を設計段階で扱う。
元メモには一切記述がなかった領域。

## 1. SSRF（最大のリスク）

利用者は Test Plan に任意の URL を入れられる。Suite はそこへバックチャネル接続する。

```
攻撃者 ──▶ Hosted Suite ──▶ http://169.254.169.254/latest/meta-data/  （クラウドメタデータ）
                       ──▶ http://10.0.0.5:6379/                      （内部 Redis）
                       ──▶ http://localhost:8080/api/                 （Suite 自身の API）
```

さらに Transcript がレスポンス全文を保持するため、**取得内容がそのまま利用者に見える**。
典型的なブラインドでない SSRF になる。

### 対策

| 対策 | 内容 |
|---|---|
| アウトバウンドの宛先フィルタ | `hosted` モードでは、名前解決後の IP がプライベート / ループバック / リンクローカル / CGNAT / マルチキャスト / IPv6 ULA に該当したら**接続を拒否**する |
| DNS リバインディング対策 | 名前解決の結果を固定してから接続する（解決済み IP に直接接続し、`Host` ヘッダで指定）。解決とチェックと接続の間に再解決を挟まない |
| リダイレクト追跡 | 各ホップで同じ検査を行う。最大 3 ホップ |
| スキーム制限 | `http` / `https` のみ。`file:` `gopher:` `ftp:` `jar:` を拒否 |
| ポート制限 | `hosted` では 80 / 443 / 8080 / 8443 等の許可リスト |
| レスポンスサイズ上限 | メタデータ 5 MB、その他 1 MB |
| タイムアウト | 接続 5 秒 / 全体 30 秒 |
| Suite 自身の API への到達禁止 | 自分の base URL / 内部ポートを明示的にブロック |
| self-hosted の既定 | `SAMLIER_OUTBOUND_ALLOW_PRIVATE=true`（社内 IdP をテストするのが主目的なので許可する）。**この差を README で明示** |

> `hosted` と `selfhosted` で既定を変えることが要点。
> 社内 IdP をテストできることが self-hosted の存在理由なので、そこでは制限しない。

## 2. XXE / XML 爆弾（自分自身への）

Suite は対象から来た XML をパースする。**攻撃対象は Suite 側でもある**。

- 全ての `DocumentBuilderFactory` / `XMLInputFactory` / `TransformerFactory` で
  `FEATURE_SECURE_PROCESSING=true`、外部一般エンティティ・外部パラメータエンティティ・DTD を無効化
- ただし **IIP-G03 のテストでは DTD 入り XML を「生成」する**必要がある。
  生成経路（`raw/`）とパース経路（`normal/`）で設定を分ける。混同しないようにパッケージを分離する
- エンティティ展開数・ネスト深さ・要素数の上限を設定（billion laughs）
- 受信 XML のサイズ上限
- XSLT Transform を含む署名は**検証時に拒否**する（署名検証における XSLT は攻撃面）

## 3. XML 署名検証の実装上の注意

Suite は「相手の署名が正しいか」を判定する側でもある。ここを間違えると誤判定を配る。

- `Reference` の URI が **署名対象の要素を実際に指しているか**を必ず確認する（XSW 対策）
- 許可する Transform を **Enveloped Signature + C14N のみ**に限定する
- ID 属性の重複を検出する
- 署名検証は「検証が通った」だけでなく **「何が署名されていたか」** を判定に使う
- OpenSAML の `SignatureValidator` に任せきりにせず、Transcript に
  Reference URI / Transform / 使用鍵 / 署名対象要素の XPath を記録する

## 4. Test Peer の鍵管理

- Test Plan ごとに鍵ペアを生成する（共有しない）
- **秘密鍵は `/data` に平文で置かれる**ことを README に明記する。Suite は本番用途ではない
- 生成鍵は「テスト専用」であることが分かる Subject DN にする
  （例: `CN=samlier test key (DO NOT TRUST), OU=Test Plan 01K3..., O=samlier`）
- Hosted 版の鍵は Run 保持期間の経過後に削除する
- **ECP テスト（IIP-IDP14）の HTTP Basic 資格情報は永続化しない**。
  実行中のみメモリに保持し、`CaseState` にも Transcript にも書かない。
  `Authorization` ヘッダは Recorder の入口で不可逆に除去する（[02 §5.2](02-architecture.md)）。
  `RedactorTest` で「Basic 認証つき ECP 往復の後、`/data` 配下の全バイト列に
  資格情報が現れないこと」を検証する
- **既知の固定鍵を配布しない**（配布すると、その鍵を信頼している実装への攻撃に使われる）

## 5. Open Redirect / 反射型 XSS

Suite は SAML メッセージ中の URL にブラウザをリダイレクトする場面がある（SP テストの ACS など）。

- リダイレクト先は **対象メタデータに記載された URL に限定**する
- 例外的に任意 URL へリダイレクトするテストがある場合は、中間確認ページを挟む
- Transcript の XML を UI に表示する際、**必ずエスケープする**。
  対象から来た XML には任意のスクリプトが含まれうる
- `report.html` は自己完結ファイルとして配布されるため、埋め込む対象由来データのエスケープを特に厳格にする
### ★ オリジン分離は「検討」ではなく要件

Test Peer は**不正な Assertion も受け取って観測する**のが仕事であり、検証が意図的に緩い。
一方 `app` 側には管理トークン（[09 D-09](09-open-decisions.md)）に紐づくセッションがある。
同一オリジンに置くと、Test Peer に届いた対象由来のコンテンツが管理セッションに触れうる。

| 配備モード | 規範レベル |
|---|---|
| **Hosted** | **MUST**。`app.<domain>` と `peer.<domain>` を別オリジンにする。分離できない構成では起動を拒否する |
| **self-hosted（インターネット公開）** | **SHOULD**。`SAMLIER_PEER_BASE_URL` の設定を強く推奨し、未設定なら起動時に警告する |
| **self-hosted（閉じたネットワーク）** | **MAY**。同一オリジンでよい（管理トークンも実質意味を持たない） |

`SAMLIER_MODE=hosted` かつ `SAMLIER_PEER_BASE_URL` が `SAMLIER_PUBLIC_BASE_URL` と
同一オリジンなら**起動時エラー**にする。

### 管理画面の CSP

```
Content-Security-Policy:
  default-src 'none';
  script-src 'self' 'nonce-{per-response-random}';
  style-src  'self' 'nonce-{per-response-random}';
  connect-src 'self';
  img-src    'self' data:;
  form-action 'self';
  frame-ancestors 'none';
  base-uri   'none';
  object-src 'none'
```

- `nonce` は **レスポンスごとに新しい 128bit 以上の乱数**を生成する。
  固定値やビルド時定数にしない（それでは XSS を止められない）
- `'unsafe-inline'` / `'unsafe-eval'` / `'strict-dynamic'` を使わない
- 管理画面には外部由来のリソース（画像・スクリプト・iframe・フォント）を一切置かない
- `peer` オリジンには別の（より緩くてよい）CSP を当てる。共有しない

## 6. Hosted 版の悪用防止

| リスク | 対策 |
|---|---|
| 他人の IdP / SP へのスキャンや DoS の踏み台 | レート制限、同一ターゲットへの同時 Run 数制限、Test Plan 作成時に「テスト対象を運用する権限があること」の確認チェックボックス |
| 大量の Test Plan 作成 | IP / アカウント単位の上限 |
| 公開結果を使った他社製品への風評操作 | 製品名は self-declared であることの明示。**削除要請の窓口を用意する**。Hosted 版の利用規約に記載 |
| 実ユーザーの個人情報の公開 | [06](06-results-and-publication.md) の既定マスク + 公開前プレビュー |

## 7. Phase 4 に向けた注意

Phase 4 では「攻撃用の SAML メッセージを生成するツール」になる。

- 生成した攻撃用メッセージを**そのまま第三者に送れる汎用ツールとして提供しない**
  （Test Plan で指定した対象にのみ送る）
- README に「自分が運用権限を持つシステムに対してのみ使うこと」を明記
- ただし OSS である以上、コードは誰でも改変できる。過度な制限は意味がないので、
  **利用規約と設計上の既定値**で線を引く
