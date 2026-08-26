# tools/

設計ゲート G1 のツール。**生成・文書化・検証を 3 本に分離**している。

| スクリプト | 役割 | ネットワーク | authoring 入力 | 再現性 |
|---|---|---|---|---|
| `g1_author.py` | 原文 → `tests/*.yaml` を**生成** | 不要（`build/spec-cache/` を読む） | **必要**（gitignored） | 初回の起票のみ |
| `g1_docgen.py` | `tests/coverage.yaml` → `docs/04` | **不要** | **不要** | ✅ 別 checkout でも同一出力 |
| `g1_validate.py` | コミット済み成果物を**独立検証** | 必要（`--offline` でキャッシュ可） | **不要** | ✅ |

依存: `PyYAML`（`g1_docgen.py` / `g1_validate.py`）

```bash
python3 tools/g1_docgen.py            # docs/04 を再生成
python3 tools/g1_docgen.py --check    # 生成物と一致するかだけ確認（CI 用）
python3 tools/g1_validate.py          # 原文を取得して照合 → build/spec-reconcile-report.json（Git 管理外）
python3 tools/g1_validate.py --offline
```

## validator は生成処理から独立している

`g1_validate.py` は **コミット済みの `tests/*.yaml` を読み込んで照合するだけ**で、
一切の値を書き戻さない。特に:

- 原文のダイジェストは**取得したものと `specs.yaml` の記録値を比較**する
  （生成時にその場で書き込む方式では、原文が変われば記録値も変わってしまい検証にならない）
- 節・句のダイジェスト、非規範スパン、オフセット範囲を**再計算して**記録値と突き合わせる
- 句の**出現回数**を数え、複数一致（曖昧な locator）を検出する
- `predicates.yaml` の `observed` の有無、`configuration_failure_semantics` の明示などを検査する

> この分離を入れた直後、validator が `IIP-EXT01.b` / `.c` が**同じ文字列を指していた**ことを
> 検出した。生成と検証が同じコードだった間は気づけなかった欠陥である。

## 再生成性

`docs/04` は `coverage.yaml` **だけ**から生成でき、`g1_docgen.py --check` で一致を検証できる。
`coverage.yaml` 自体の再生成には authoring 入力（原文の句を含むため配布しない）が要るが、
**その正しさは `g1_validate.py` が原文と突き合わせて独立に検証する**ので、
配布物だけで検証は完結する。

## `g1_authoring.py`（gitignored）

各義務に対応する**原文の句そのもの**を持つ。[docs/09 D-11](../docs/09-open-decisions.md)
（原文を転載しない）と両立させるため配布しない。`coverage.yaml` にはオフセットと
ダイジェストだけが残る。
