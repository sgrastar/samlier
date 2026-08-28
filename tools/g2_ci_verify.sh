#!/usr/bin/env bash
# Trusted external wrapper for G2 design approval verification.
set -euo pipefail

PY="${PY:-python3}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${G2_REPO_ROOT:-$(cd "$HERE/.." && pwd)}"
PIN="${G2_TOOLS_COMMIT:-}"

if ! [[ "$PIN" =~ ^[0-9a-f]{40}$ ]]; then
  echo "[g2-ci-verify] G2_TOOLS_COMMIT must be a complete immutable commit SHA" >&2
  exit 2
fi
if [[ "$(git -C "$REPO" rev-parse --verify --quiet "${PIN}^{commit}" || true)" != "$PIN" ]]; then
  echo "[g2-ci-verify] G2_TOOLS_COMMIT cannot be resolved exactly" >&2
  exit 2
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/g2-ci-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/tools"
for file in tools/g2_trusted_verify.py tools/g2_validate.py; do
  git -C "$REPO" show "${PIN}:${file}" > "$TMP/$file"
done

env -u PYTHONPATH -u G2_VALIDATOR_COMMIT -u G2_RUNNER_COMMIT \
  G2_REPO_ROOT="$REPO" \
  G2_RUNNER_COMMIT="$PIN" \
  G2_VALIDATOR_COMMIT="$PIN" \
  "$PY" -I "$TMP/tools/g2_trusted_verify.py"
