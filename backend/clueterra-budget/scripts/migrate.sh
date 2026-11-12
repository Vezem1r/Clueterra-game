#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-clueterra-postgres}"

echo "Running Flyway migrate for clueterra-budget via Docker..."
docker run --rm \
  --network "container:$POSTGRES_CONTAINER" \
  -v "$PROJECT_DIR/src/main/resources/db/migration:/flyway/migrations" \
  flyway/flyway:10 \
  -url="jdbc:postgresql://localhost:5432/${DB_NAME:-clueterra}" \
  -user="${DB_USER:-clueterra}" \
  -password="${DB_PASSWORD}" \
  -schemas="cl_budget_schema,cl_budget_staging,cl_budget_sch_schema" \
  -defaultSchema="cl_budget_schema" \
  -createSchemas="true" \
  -baselineOnMigrate="true" \
  -locations="filesystem:/flyway/migrations" \
  migrate

echo "Budget migration complete."
