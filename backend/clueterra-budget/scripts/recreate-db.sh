#!/bin/bash
set -euo pipefail

DB_NAME="${DB_NAME:-clueterra}"
DB_USER="${DB_USER:-clueterra}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-clueterra-postgres}"

echo "Dropping and recreating BudgetGuessr schemas inside Docker container..."

docker exec -i "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<SQL
DROP SCHEMA IF EXISTS cl_budget_schema CASCADE;
DROP SCHEMA IF EXISTS cl_budget_staging CASCADE;
DROP SCHEMA IF EXISTS cl_budget_sch_schema CASCADE;
CREATE SCHEMA cl_budget_schema;
CREATE SCHEMA cl_budget_staging;
CREATE SCHEMA cl_budget_sch_schema;
SQL

echo "BudgetGuessr schemas recreated. Running migrations..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "$SCRIPT_DIR/migrate.sh"

echo "Granting BudgetGuessr database role..."
: "${BUDGET_DB_PASSWORD:?BUDGET_DB_PASSWORD must be set}"
docker exec -i -e BUDGET_DB_PASSWORD "$POSTGRES_CONTAINER" psql -X -U "$DB_USER" -d "$DB_NAME" \
  < "$SCRIPT_DIR/../../../scripts/db/budget-role.sql"
