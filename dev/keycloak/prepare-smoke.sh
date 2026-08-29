#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/dev/keycloak/compose.yml")

UP_MODE=(--build)
if [[ "${1:-}" == "--no-build" ]]; then
  UP_MODE=(--no-build)
elif [[ $# -gt 0 ]]; then
  echo "usage: $0 [--no-build]" >&2
  exit 2
fi

"${COMPOSE[@]}" up -d "${UP_MODE[@]}"

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
