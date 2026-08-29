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

SAMLIER_IMAGE="${SAMLIER_IMAGE:-samlier:keycloak-smoke}"
export SAMLIER_IMAGE
PROVIDED_IMAGE_DIGEST="${SAMLIER_IMAGE_DIGEST:-}"
# Compose resolves required environment substitutions even for `build`.
# This value is never used to start Samlier; it is replaced with the inspected digest below.
export SAMLIER_IMAGE_DIGEST="${PROVIDED_IMAGE_DIGEST:-pending-image-build}"
if [[ "$BUILD_IMAGE" == "1" ]]; then
  "${COMPOSE[@]}" build samlier
fi

ACTUAL_IMAGE_DIGEST="$(docker image inspect --format '{{.Id}}' "$SAMLIER_IMAGE")"
if [[ -n "$PROVIDED_IMAGE_DIGEST" && "$PROVIDED_IMAGE_DIGEST" != "$ACTUAL_IMAGE_DIGEST" ]]; then
  echo "SAMLIER_IMAGE_DIGEST does not match $SAMLIER_IMAGE" >&2
  echo "expected: $ACTUAL_IMAGE_DIGEST" >&2
  echo "provided: $PROVIDED_IMAGE_DIGEST" >&2
  exit 2
fi
export SAMLIER_IMAGE_DIGEST="$ACTUAL_IMAGE_DIGEST"

"${COMPOSE[@]}" up -d --no-build

for _ in $(seq 1 90); do
  if curl -fsS http://localhost:8080/api/health >/dev/null \
      && curl -fsS http://localhost:8180/realms/samlier/.well-known/openid-configuration >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS http://localhost:8080/api/health >/dev/null
curl -fsS http://localhost:8180/realms/samlier/.well-known/openid-configuration >/dev/null

SMOKE_ARGS=(fixture
  --samlier-base http://localhost:8080
  --keycloak-base http://localhost:8180
  --target-metadata-url http://keycloak:8080/realms/samlier/protocol/saml/descriptor)
if [[ "${SAMLIER_SMOKE_MANUAL:-0}" == "1" ]]; then
  SMOKE_ARGS+=(--manual)
fi
"${PY:-python3}" "$ROOT/dev/keycloak/smoke.py" "${SMOKE_ARGS[@]}"
