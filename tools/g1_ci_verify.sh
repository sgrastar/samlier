#!/usr/bin/env bash
# g1_ci_verify.sh — G1 承認検証の信頼された外部ラッパー
#
# runner（g1_trusted_verify.py）自身が改変されると、runner 内の制約
# （C..A の制限、validator の取得元固定）は削除できてしまう。
# このラッパーは **固定 SHA から runner 一式を取り出して** 隔離実行する。
#
# ラッパー自身も「リポジトリ内のコピー」である以上、最後の一枚は CI 設定側で固定する。
# CI ではこのファイルを呼ぶのではなく、**中身を workflow にインラインで書く**か、
# 固定 SHA から取り出したものを実行すること（下の CI スニペットを参照）。
#
#   使い方:
#     G1_TOOLS_COMMIT=<40桁SHA> tools/g1_ci_verify.sh [--offline]
#
#   環境変数:
#     G1_TOOLS_COMMIT      runner / validator の取得元（40 桁完全 SHA。必須）
#     G1_REPO_ROOT         検査対象リポジトリ（省略時はこのスクリプトのリポジトリ）
#     PY                   python 実行ファイル（省略時は python3）
#
#   ★ validator の取得元は **常に G1_TOOLS_COMMIT** に固定する。
#     環境に残った G1_VALIDATOR_COMMIT は無視する（ambient 継承による
#     「runner は正しいが validator だけ別 commit」を防ぐ）。
#     別々の anchor を使う場合は --validator-commit=<40桁SHA> を明示すること。
set -euo pipefail

# --validator-commit=<sha> を先に取り出す（明示指定のみ許可。環境変数からは受け取らない）
VALIDATOR_COMMIT=""
ARGS=()
for a in "$@"; do
  case "$a" in
    --validator-commit=*) VALIDATOR_COMMIT="${a#*=}" ;;
    *) ARGS+=("$a") ;;
  esac
done

PY="${PY:-python3}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${G1_REPO_ROOT:-$(cd "$HERE/.." && pwd)}"

if [[ -z "${G1_TOOLS_COMMIT:-}" ]]; then
  echo "[ci-verify] G1_TOOLS_COMMIT が未設定です。runner の取得元を 40 桁の完全 SHA で固定してください" >&2
  exit 2
fi
if ! [[ "$G1_TOOLS_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "[ci-verify] G1_TOOLS_COMMIT は 40 桁の完全な SHA-1 のみ（HEAD / main などの可変 ref は不可）" >&2
  exit 2
fi
RESOLVED="$(git -C "$REPO" rev-parse --verify --quiet "${G1_TOOLS_COMMIT}^{commit}" || true)"
if [[ "$RESOLVED" != "$G1_TOOLS_COMMIT" ]]; then
  echo "[ci-verify] G1_TOOLS_COMMIT ${G1_TOOLS_COMMIT:0:12} を commit として完全一致で解決できません" >&2
  exit 2
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/g1-ci-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/tools"
for f in tools/g1_trusted_verify.py tools/g1_validate.py tools/g1_extract.py; do
  git -C "$REPO" show "${G1_TOOLS_COMMIT}:${f}" > "$TMP/$f"
done

echo "[ci-verify] runner の取得元: ${G1_TOOLS_COMMIT:0:12} (外部固定)"
echo "[ci-verify] 検査対象リポジトリ: $REPO"

# validator の取得元は既定で G1_TOOLS_COMMIT と同一。
# ambient な G1_VALIDATOR_COMMIT は **無視する**（-u で環境から落とす）。
if [[ -n "$VALIDATOR_COMMIT" ]]; then
  if ! [[ "$VALIDATOR_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
    echo "[ci-verify] --validator-commit は 40 桁の完全な SHA-1 のみ" >&2; exit 2
  fi
  echo "[ci-verify] ⚠ validator を runner とは別の commit に固定しています: ${VALIDATOR_COMMIT:0:12}" >&2
else
  VALIDATOR_COMMIT="$G1_TOOLS_COMMIT"
fi
echo "[ci-verify] validator の取得元: ${VALIDATOR_COMMIT:0:12}"

env -u PYTHONPATH -u G1_VALIDATOR_COMMIT -u G1_RUNNER_COMMIT \
    G1_REPO_ROOT="$REPO" \
    G1_RUNNER_COMMIT="$G1_TOOLS_COMMIT" \
    G1_VALIDATOR_COMMIT="$VALIDATOR_COMMIT" \
    "$PY" -I "$TMP/tools/g1_trusted_verify.py" ${ARGS[@]+"${ARGS[@]}"}
