#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/dev/keycloak/compose.yml")
TMP="$(mktemp -d "${TMPDIR:-/tmp}/samlier-keycloak-XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

"${COMPOSE[@]}" up -d --build

for _ in $(seq 1 90); do
  if curl -fsS http://localhost:8080/api/health >/dev/null \
      && curl -fsS http://localhost:8180/realms/samlier/.well-known/openid-configuration >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS http://localhost:8080/api/health >/dev/null
curl -fsS http://localhost:8180/realms/samlier/.well-known/openid-configuration >/dev/null

TOKEN="$(curl -fsS -X POST http://localhost:8180/realms/master/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode client_id=admin-cli \
  --data-urlencode username=admin \
  --data-urlencode password=admin \
  --data-urlencode grant_type=password \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["access_token"])')"

curl -fsS -X POST http://localhost:8080/api/plans \
  -H 'Content-Type: application/json' \
  --data-binary @- >"$TMP/plan.json" <<'JSON'
{
  "name": "Keycloak 26.7.2 IdP smoke",
  "profile": "IDP_CORE",
  "targetKind": "IDP",
  "targetEntityId": "http://localhost:8180/realms/samlier",
  "metadataSourceKind": "URL",
  "metadataSourceLocation": "http://keycloak:8080/realms/samlier/protocol/saml/descriptor",
  "suiteMetadataDelivery": "HTTP_URL",
  "declaredFeatures": {},
  "parameters": {
    "clockSkewToleranceSeconds": 180,
    "metadataRefreshWaitSeconds": 300,
    "testUserHint": "samlier-m0-user"
  },
  "interaction": { "allowBrowserSteps": true, "allowAttestation": true }
}
JSON

PLAN_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["plan"]["id"])' "$TMP/plan.json")"
curl -fsS "http://localhost:8080/p/$PLAN_ID/metadata" >"$TMP/samlier-metadata.xml"

curl -fsS -X POST http://localhost:8180/admin/realms/samlier/client-description-converter \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/xml' \
  --data-binary @"$TMP/samlier-metadata.xml" >"$TMP/keycloak-client.json"
curl -fsS -X POST http://localhost:8180/admin/realms/samlier/clients \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  --data-binary @"$TMP/keycloak-client.json"

curl -fsS -X POST "http://localhost:8080/api/plans/$PLAN_ID/runs" >"$TMP/run.json"
RUN_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$TMP/run.json")"
curl -fsS -X POST "http://localhost:8080/api/runs/$RUN_ID/preflight" >"$TMP/preflight.json"

printf 'Samlier M0 and Keycloak are ready.\n'
printf 'Open: http://localhost:8080/p/%s/start/m0-roundtrip?run=%s\n' "$PLAN_ID" "$RUN_ID"
printf 'Log in with samlier-m0-user / samlier-m0-password.\n'
printf 'Then confirm the Run is COMPLETED in http://localhost:8080.\n'
