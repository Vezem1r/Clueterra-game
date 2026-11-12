#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="${CLUETERRA_COMPOSE_FILE:-$ROOT_DIR/docker/docker-compose.yml}"
COMPOSE_DIR="$(cd "$(dirname "$COMPOSE_FILE")" && pwd)"

if [ -f "$ROOT_DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

required=(
  MINIO_ACCESS_KEY
  MINIO_SECRET_KEY
  MINIO_API_SECRET_KEY
  MINIO_QUESTIONS_SECRET_KEY
  MINIO_BUDGET_SECRET_KEY
)

for variable in "${required[@]}"; do
  if [ -z "${!variable:-}" ]; then
    echo "Missing required MinIO service credential: $variable" >&2
    exit 1
  fi
done

if [ "${MINIO_PRODUCTION_MODE:-false}" = "true" ] \
  && { [ "$MINIO_ACCESS_KEY" = "minioadmin" ] \
    || [ "$MINIO_SECRET_KEY" = "minioadmin" ] \
    || [ "${#MINIO_SECRET_KEY}" -lt 20 ]; }; then
  echo "Production MinIO root credentials must be unique and at least 20 characters long." >&2
  exit 1
fi

case "$MINIO_ACCESS_KEY" in
  clueterra-api|clueterra-questions|clueterra-budget)
    echo "MinIO root access key must not reuse an application identity." >&2
    exit 1
    ;;
esac

for variable in MINIO_API_SECRET_KEY MINIO_QUESTIONS_SECRET_KEY MINIO_BUDGET_SECRET_KEY; do
  secret="${!variable}"
  if [ "${#secret}" -lt 20 ] || [[ "$secret" == replace-with-* ]] \
    || [ "$secret" = "$MINIO_SECRET_KEY" ]; then
    echo "$variable must be a unique random secret of at least 20 characters." >&2
    exit 1
  fi
done

if [ "$MINIO_API_SECRET_KEY" = "$MINIO_QUESTIONS_SECRET_KEY" ] \
  || [ "$MINIO_API_SECRET_KEY" = "$MINIO_BUDGET_SECRET_KEY" ] \
  || [ "$MINIO_QUESTIONS_SECRET_KEY" = "$MINIO_BUDGET_SECRET_KEY" ]; then
  echo "Each application must use a distinct MinIO secret key." >&2
  exit 1
fi

docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_FILE" up -d minio

for attempt in {1..30}; do
  if docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_FILE" \
    exec -T minio mc ready local >/dev/null 2>&1; then
    break
  fi
  if [ "$attempt" -eq 30 ]; then
    echo "MinIO did not become ready in time." >&2
    exit 1
  fi
  sleep 2
done

docker compose --project-directory "$COMPOSE_DIR" -f "$COMPOSE_FILE" \
  exec -T \
  -e MINIO_API_SECRET_KEY="$MINIO_API_SECRET_KEY" \
  -e MINIO_QUESTIONS_SECRET_KEY="$MINIO_QUESTIONS_SECRET_KEY" \
  -e MINIO_BUDGET_SECRET_KEY="$MINIO_BUDGET_SECRET_KEY" \
  minio sh /opt/clueterra/minio/provision.sh
