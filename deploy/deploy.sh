#!/usr/bin/env bash
set -euo pipefail

umask 077

readonly APP_DIR=/opt/samlscope
readonly DATA_DIR=/srv/samlscope/data
readonly BACKUP_DIR=/srv/samlscope/backups
readonly COMPOSE_FILE="$APP_DIR/compose.yaml"
readonly ENV_FILE="$APP_DIR/.env"
readonly LOCK_FILE="$APP_DIR/deploy.lock"

image_reference="${1:-}"
suite_image_digest="${2:-}"

if [[ ! "$image_reference" =~ ^ghcr\.io/sgrastar/samlscope@sha256:[0-9a-f]{64}$ ]]; then
  echo "The image must be the digest-pinned ghcr.io/sgrastar/samlscope image." >&2
  exit 2
fi
if [[ ! "$suite_image_digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
  echo "The suite image digest must be a lowercase SHA-256 digest." >&2
  exit 2
fi
if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing $COMPOSE_FILE" >&2
  exit 2
fi

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "Another SAMLscope deployment is already running." >&2
  exit 3
fi

mkdir -p "$DATA_DIR" "$BACKUP_DIR"
docker pull "$image_reference"

previous_environment=""
if [[ -f "$ENV_FILE" ]]; then
  previous_environment="$(mktemp "$APP_DIR/.env.previous.XXXXXX")"
  cp "$ENV_FILE" "$previous_environment"
fi

write_environment() {
  local reference="$1"
  local digest="$2"
  local temporary
  temporary="$(mktemp "$APP_DIR/.env.next.XXXXXX")"
  printf 'SAMLSCOPE_IMAGE=%s\nSAMLSCOPE_IMAGE_DIGEST=%s\n' "$reference" "$digest" > "$temporary"
  chmod 600 "$temporary"
  mv "$temporary" "$ENV_FILE"
}

start_current_environment() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --remove-orphans
}

if [[ -f "$ENV_FILE" ]]; then
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" stop samlscope
fi

backup="$BACKUP_DIR/predeploy-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
if ! tar --xattrs --acls -C "$DATA_DIR" -czf "$backup" .; then
  if [[ -n "$previous_environment" ]]; then
    cp "$previous_environment" "$ENV_FILE"
    start_current_environment
  fi
  echo "Could not create the pre-deployment backup." >&2
  exit 4
fi

write_environment "$image_reference" "$suite_image_digest"
if ! start_current_environment; then
  deployment_failed=1
else
  deployment_failed=0
  for attempt in $(seq 1 30); do
    if response="$(curl --fail --silent --show-error --max-time 5 https://app.samlscope.com/api/health 2>/dev/null)" \
        && [[ "$response" == *'"status":"ok"'* ]]; then
      deployment_failed=0
      break
    fi
    deployment_failed=1
    sleep 2
  done
fi

if (( deployment_failed != 0 )); then
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" logs --tail 100 samlscope >&2 || true
  if [[ -n "$previous_environment" ]]; then
    cp "$previous_environment" "$ENV_FILE"
    start_current_environment || true
  fi
  echo "Deployment failed. The previous image was restored; data backup: $backup" >&2
  exit 5
fi

if [[ -n "$previous_environment" ]]; then
  rm -f "$previous_environment"
fi
echo "SAMLscope deployment healthy: $image_reference"
echo "Pre-deployment backup: $backup"
