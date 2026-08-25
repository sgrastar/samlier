# 07. 配布とネットワーク要件

## 1. Docker 単一イメージ

元メモの方針（Cloudflare Workers 版と Docker 版の二重実装をしない）は正しい。
Hosted 版と self-hosted 版で **同じイメージ・同じ Test Runner・同じ判定ロジック**を使う。

```bash
docker run \
  -p 8080:8080 \
  -v samlier-data:/data \
  -e SAMLIER_PUBLIC_BASE_URL=https://samltest.example \
  samlier/suite:0.1.0
```

- ベースイメージ: distroless または Alpine + JRE 21
- マルチアーキ: `linux/amd64`, `linux/arm64`（Apple Silicon 開発者が多い）
- 状態は全て `/data` 配下（SQLite ファイル、生成鍵、Transcript）
- 非 root で動作

### 環境変数

| 変数 | 既定 | 説明 |
|---|---|---|
| `SAMLIER_PUBLIC_BASE_URL` | `http://localhost:8080` | **最重要**。Suite の外部から見える URL。メタデータの全エンドポイント URL がこれを基に生成される |
| `SAMLIER_MODE` | `selfhosted` | `selfhosted` / `hosted` |
| `SAMLIER_DATA_DIR` | `/data` | |
| `SAMLIER_HTTP_PORT` | `8080` | |
| `SAMLIER_TLS_CERT` / `_KEY` | なし | 直接 TLS を終端する場合 |
| `SAMLIER_OUTBOUND_ALLOW_PRIVATE` | `true`(selfhosted) / `false`(hosted) | プライベート IP へのバックチャネル接続を許可するか。[08](08-suite-security.md) 参照 |
| `SAMLIER_OUTBOUND_ALLOW_INSECURE_TLS` | `false` | 対象の自己署名証明書を受け入れるか |
| `SAMLIER_PUBLISH_ENABLED` | `false`(selfhosted) / `true`(hosted) | 共有 URL 発行の可否 |
| `SAMLIER_RUN_RETENTION_DAYS` | `30` | |

## 2. ★ ネットワーク要件（元メモに欠けていた最重要事項）

「`docker run -p 8080:8080` して `http://localhost:8080` を開くだけ」は
**フロントチャネルのテストにしか成立しない**。

### 経路は 2 系統ある

```
(A) フロントチャネル（ブラウザ経由）
    Browser ──▶ Suite      Suite が localhost でも動く
    Browser ──▶ Target     ブラウザから両方に届けばよい

(B) バックチャネル（サーバ間直接）
    Target ──▶ Suite       ★ Target から Suite に到達できる必要がある
      - メタデータ / MDQ の取得      （IIP-MD01〜MD04, MD07, MD10-12）
      - SOAP による Single Logout    （IIP-SP14, IIP-IDP17）
    Suite ──▶ Target       Suite から Target に到達できる必要がある
      - Target のメタデータ取得
      - ECP / PAOS                   （IIP-IDP13〜16）
      - SOAP SLO
```

**(B) の `Target → Suite` 方向が `http://localhost:8080` では成立しない。**
[04](04-requirement-coverage.md) の集計の通り、対象側の設定を要するテストは全体の約 4 割であり、
その多くがこの経路を必要とする。

### ★ 到達性は Preflight だけでは判定できない

`Target → Suite` の到達性は、**Suite 自身が公開 URL に接続できても証明にならない**。
NAT、split-horizon DNS、対象側の egress ファイアウォール、対象のプロキシ設定など、
Suite からは見えない要因で失敗しうる。

そこで **主張（asserted）と確認（confirmed）を分ける**。

```
Preflight（Suite が単独でできること）
  ├ Suite → 自身の PUBLIC_BASE_URL に接続できるか
  ├ Suite → 対象メタデータ URL に接続できるか
  └ 結果: reachability = ASSERTED  （まだ「たぶん届く」でしかない）

到達性チャレンジ（対象を関与させる）
  ├ Suite がメタデータ URL に一度限りの nonce を仕込む
  ├ 利用者に「対象側でメタデータを再読込してください」と指示（WAITING_CONFIG）
  ├ Suite が nonce 付き URL への inbound リクエストを Transcript で観測する
  │   （送信元 IP・User-Agent・TLS 情報も記録）
  └ 結果: reachability = CONFIRMED
```

**`requires.reachability: target_to_suite` を宣言したケースは、
`CONFIRMED` になるまで実行しない**（[05 §2.2](05-test-definition-format.md)）。
`ASSERTED` のままなら `NOT_VERIFIED(target_unreachable)` になり、
MUST 義務であれば `conformance = INDETERMINATE` / `completeness = INCOMPLETE` になる（[03 §7.2](03-test-model.md)）。

SOAP SLO のようにメタデータ取得を伴わない経路は、
**最初の inbound SOAP 要求を観測した時点で確認済みに昇格**させる。

### 動作モード

| モード | 条件 | 実行できるテスト |
|---|---|---|
| **Local-only** | `PUBLIC_BASE_URL` が localhost。到達性チャレンジが成立しない | フロントチャネルのみ。メタデータは手動配布。IIP-MD01〜04 等は `NOT_VERIFIED(target_unreachable)` |
| **Reachable (asserted)** | Suite が到達可能そうな URL を持つが、まだ対象からの inbound を観測していない | フロントチャネル + Suite 発のバックチャネル（対象メタデータ取得、ECP、SOAP 送信） |
| **Reachable (confirmed)** | 対象からの inbound を実際に観測した | 全テスト |
| **Hosted** | 公式 Hosted 版。対象がインターネット上にある | **到達確認は Hosted でも必要**。Suite が公開 URL を持つことは「対象の egress が Suite に届く」ことを意味しない（対象側 FW・プロキシ・許可リスト）。Hosted でも `CONFIRMED` になるまでバックチャネル系は `NOT_VERIFIED(target_unreachable)`。社内 IdP はそもそもテストできない |

UI は Test Plan 作成時に現在のモードを表示し、
**「この構成では現在 N 件の MUST 義務が未検証になります」と件数付きで事前に警告する**。
チャレンジが成功したら件数をリアルタイムに減らす。

### 到達可能な URL を得る手段（README に書く）

1. Suite をパブリック IP / 社内 DNS のあるホストで動かす（推奨）
2. リバースプロキシ（nginx / Caddy）配下に置き、`PUBLIC_BASE_URL` を実 URL にする
3. トンネル（`cloudflared tunnel` / `ngrok`）で一時 URL を得る
   → **Phase 2 で `docker compose` のオプションプロファイルとして同梱を検討**
4. 公式 Hosted 版を使う（対象がインターネットから到達できる場合のみ）

## 3. ★ HTTPS 要件

- 多くの SP / IdP は **`http://` の ACS URL / SSO URL を拒否する**（設定で強制している実装が多い）
- ブラウザの `SameSite` Cookie の既定値により、**クロスサイト POST（HTTP-POST バインディング）で
  Cookie が落ちる**ことがある。`Secure` + `SameSite=None` が必要で、これは HTTPS 必須
- したがって **実用上 HTTPS がほぼ必須**である

対応:

| 方法 | 用途 |
|---|---|
| リバースプロキシで TLS 終端 | 本番的な self-host。推奨 |
| `SAMLIER_TLS_CERT` / `_KEY` で直接終端 | 単体運用 |
| 自己署名証明書を同梱生成 | ローカル開発用。ブラウザ・対象の双方で信頼設定が必要な旨を警告する |
| トンネル | 一時利用 |

Preflight で `PUBLIC_BASE_URL` が `http://` かつ非 localhost の場合は**警告を出す**。

## 4. ★ 時刻

コンテナの時刻がずれると **全テストが壊れる**（`NotOnOrAfter` / `IssueInstant` / `validUntil`）。

- 起動時に外部 NTP または HTTP `Date` ヘッダとの差分を測り、**1 分以上ずれていたら起動を警告**
- Preflight で対象メタデータ取得時の `Date` ヘッダと自機時刻を比較し、差分を Run に記録
- テストコードは `System.currentTimeMillis()` を直接使わず `TestContext.clock()` を経由する
  （クロックスキューテストで意図的に時刻を操作するため）

## 5. Hosted 版で追加になるもの

同じイメージだが、`SAMLIER_MODE=hosted` で有効になる。

| 機能 | 理由 |
|---|---|
| レート制限 / 同時実行数制限 | 悪用防止 |
| プライベート IP へのアウトバウンド禁止 | SSRF 対策（[08](08-suite-security.md)） |
| 結果の公開ストレージ | 共有 URL |
| 管理アクセス | Phase 1 は **Run ごとのシークレット URL**（認証なし）。将来 Authrim による OIDC ログインを追加。[09 D-09](09-open-decisions.md) |
| 保持期間の自動削除 | |

self-hosted では認証なし（信頼されたネットワーク内で使う前提）。
**README にこれを明記する**。インターネットに晒す場合は前段で認証をかけるよう指示する。

## 6. SQLite で始めることの妥当性

- self-hosted のシングルユーザー用途では十分
- Hosted 版で同時実行が増えると書き込み競合が問題になりうる（WAL モードでかなり緩和される）
- **対策**: データアクセスを薄いリポジトリ層に閉じ込め、PostgreSQL への差し替え余地を残す。
  ORM は使わず素の SQL + マイグレーションファイルで管理する
- Transcript のような大きなデータは **DB に入れず `/data` 配下のファイル**に置き、DB には参照だけ持つ

## 7. Login SP / OIDC RP と Test Peer の分離（将来の認証導入に備えて）

Hosted 版に Authrim 等でのログインを入れる際、**同じプロセスに「テスト用の SP」と
「ログイン用の SP/RP」が同居する**ことになる。Test Peer は不正な Assertion も
「受け取って観測する」のが仕事であり、**意図的に検証が緩い**。
そこに来た Assertion で管理セッションが作られると認証バイパスになる。

Phase 1 の時点で構造だけ守っておく。

- `peer/`（テスト用）と `auth/`（管理用）でセッションストア・Cookie 名・コードパスを分ける
- **別オリジンで配信する**: `app.<domain>`（UI + 管理）と `peer.<domain>`（Test Peer エンドポイント）
- `SAMLIER_PUBLIC_BASE_URL` に加えて `SAMLIER_PEER_BASE_URL` を持つ
- **`SAMLIER_MODE=hosted` では別オリジンが必須**。同一オリジンなら**起動時エラー**にする
- self-hosted で未設定なら同一オリジンにフォールバックし、**起動時に警告**を出す
  （閉じたネットワークでの単純構成は壊さない）。規範レベルは [08 §5](08-suite-security.md)

詳細は [09 D-09](09-open-decisions.md) を参照。
