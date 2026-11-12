#!/bin/bash
set -euo pipefail

DB_NAME="${DB_NAME:-clueterra}"
DB_USER="${DB_USER:-clueterra}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-clueterra-postgres}"

echo "Dropping and recreating API schemas inside Docker container..."

docker exec -i "$POSTGRES_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<SQL
DROP SCHEMA IF EXISTS cl_api_schema CASCADE;
DROP SCHEMA IF EXISTS cl_api_sch_schema CASCADE;
CREATE SCHEMA cl_api_schema;
CREATE SCHEMA cl_api_sch_schema;
SQL

echo "API schemas recreated. Running migrations..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "$SCRIPT_DIR/migrate.sh"
