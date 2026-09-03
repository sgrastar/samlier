#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/dev/keycloak/compose.yml")

BUILD_IMAGE=1
if [[ "${1:-}" == "--no-build" ]]; then
  BUILD_IMAGE=0
elif [[ $# -gt 0 ]]; then
  echo "usage: $0 [--no-build]" >&2
  exit 2
fi

SAMLSCOPE_IMAGE="${SAMLSCOPE_IMAGE:-samlscope:keycloak-smoke}"
export SAMLSCOPE_IMAGE
PROVIDED_IMAGE_DIGEST="${SAMLSCOPE_IMAGE_DIGEST:-}"
# Compose resolves required environment substitutions even for `build`.
# This value is never used to start SAMLscope; it is replaced with the inspected digest below.
export SAMLSCOPE_IMAGE_DIGEST="${PROVIDED_IMAGE_DIGEST:-pending-image-build}"
if [[ "$BUILD_IMAGE" == "1" ]]; then
  "${COMPOSE[@]}" build samlscope
fi

ACTUAL_IMAGE_DIGEST="$(docker image inspect --format '{{.Id}}' "$SAMLSCOPE_IMAGE")"
if [[ -n "$PROVIDED_IMAGE_DIGEST" && "$PROVIDED_IMAGE_DIGEST" != "$ACTUAL_IMAGE_DIGEST" ]]; then
  echo "SAMLSCOPE_IMAGE_DIGEST does not match $SAMLSCOPE_IMAGE" >&2
  echo "expected: $ACTUAL_IMAGE_DIGEST" >&2
  echo "provided: $PROVIDED_IMAGE_DIGEST" >&2
  exit 2
fi
export SAMLSCOPE_IMAGE_DIGEST="$ACTUAL_IMAGE_DIGEST"

"${COMPOSE[@]}" up -d --no-build

for _ in $(seq 1 90); do
  if curl -fsS http://localhost:8080/api/health >/dev/null \
      && curl -fsS http://localhost:8180/realms/samlscope/.well-known/openid-configuration >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS http://localhost:8080/api/health >/dev/null
curl -fsS http://localhost:8180/realms/samlscope/.well-known/openid-configuration >/dev/null

SMOKE_ARGS=(fixture
  --samlscope-base http://localhost:8080
  --keycloak-base http://localhost:8180
  --target-metadata-url http://keycloak:8080/realms/samlscope/protocol/saml/descriptor)
if [[ "${SAMLSCOPE_SMOKE_MANUAL:-0}" == "1" ]]; then
  SMOKE_ARGS+=(--manual)
fi
"${PY:-python3}" "$ROOT/dev/keycloak/smoke.py" "${SMOKE_ARGS[@]}"
