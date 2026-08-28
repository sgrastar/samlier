#!/usr/bin/env bash
# g1_ci_verify.sh — Trusted external wrapper for G1 approval verification
#
# If the runner (g1_trusted_verify.py) itself is modified, its constraints
# (the C..A restriction and pinned validator source) can be removed.
# This wrapper **extracts the complete runner from a pinned SHA** and runs it in isolation.
#
# Since this wrapper is also a copy in the repository, the final pin must be in CI configuration.
# CI should **inline this file's contents in the workflow** or run a copy extracted from a pinned
# SHA (see the CI snippet below), rather than invoke this file directly.
#
#   Usage:
#     G1_TOOLS_COMMIT=<40-digit SHA> tools/g1_ci_verify.sh [--offline]
#
#   Environment variables:
#     G1_TOOLS_COMMIT      Runner / validator source (full 40-character SHA; required)
#     G1_REPO_ROOT         Target repository (this script's repository if omitted)
#     PY                   Python executable (python3 if omitted)
#
#   ★ The validator source is **always pinned to G1_TOOLS_COMMIT**.
#     Ignore any inherited G1_VALIDATOR_COMMIT (to prevent a correct runner from
#     using a validator from a different commit through ambient inheritance).
#     To use separate anchors, explicitly specify --validator-commit=<40-digit SHA>.
set -euo pipefail

# Extract --validator-commit=<sha> first (explicit argument only; never accept it from the environment).
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
  echo "[ci-verify] G1_TOOLS_COMMIT is unset; pin the runner source to a full 40-character SHA" >&2
  exit 2
fi
if ! [[ "$G1_TOOLS_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "[ci-verify] G1_TOOLS_COMMIT must be a full 40-character SHA-1 (mutable refs such as HEAD / main are not allowed)" >&2
  exit 2
fi
RESOLVED="$(git -C "$REPO" rev-parse --verify --quiet "${G1_TOOLS_COMMIT}^{commit}" || true)"
if [[ "$RESOLVED" != "$G1_TOOLS_COMMIT" ]]; then
  echo "[ci-verify] G1_TOOLS_COMMIT ${G1_TOOLS_COMMIT:0:12} cannot be resolved as an exact commit" >&2
  exit 2
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/g1-ci-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/tools"
for f in tools/g1_trusted_verify.py tools/g1_validate.py tools/g1_extract.py; do
  git -C "$REPO" show "${G1_TOOLS_COMMIT}:${f}" > "$TMP/$f"
done

echo "[ci-verify] Runner source: ${G1_TOOLS_COMMIT:0:12} (externally pinned)"
echo "[ci-verify] Target repository: $REPO"

# The validator source defaults to G1_TOOLS_COMMIT.
# **Ignore** ambient G1_VALIDATOR_COMMIT (-u removes it from the environment).
if [[ -n "$VALIDATOR_COMMIT" ]]; then
  if ! [[ "$VALIDATOR_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
    echo "[ci-verify] --validator-commit must be a full 40-character SHA-1" >&2; exit 2
  fi
  echo "[ci-verify] ⚠ Validator is pinned to a different commit from the runner: ${VALIDATOR_COMMIT:0:12}" >&2
else
  VALIDATOR_COMMIT="$G1_TOOLS_COMMIT"
fi
echo "[ci-verify] Validator source: ${VALIDATOR_COMMIT:0:12}"

env -u PYTHONPATH -u G1_VALIDATOR_COMMIT -u G1_RUNNER_COMMIT \
    G1_REPO_ROOT="$REPO" \
    G1_RUNNER_COMMIT="$G1_TOOLS_COMMIT" \
    G1_VALIDATOR_COMMIT="$VALIDATOR_COMMIT" \
    "$PY" -I "$TMP/tools/g1_trusted_verify.py" ${ARGS[@]+"${ARGS[@]}"}
