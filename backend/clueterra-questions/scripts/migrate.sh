#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-clueterra-postgres}"

echo "Running Flyway migrate for clueterra-questions via Docker..."
docker run --rm \
  --network "container:$POSTGRES_CONTAINER" \
  -v "$PROJECT_DIR/src/main/resources/db/migration:/flyway/migrations" \
  flyway/flyway:10 \
  -url="jdbc:postgresql://localhost:5432/${DB_NAME:-clueterra}" \
  -user="${DB_USER:-clueterra}" \
  -password="${DB_PASSWORD}" \
  -schemas="cl_schema,cl_sch_schema,cl_staging" \
  -createSchemas="true" \
  -baselineOnMigrate="true" \
  -locations="filesystem:/flyway/migrations" \
  migrate

echo "Questions migration complete."
