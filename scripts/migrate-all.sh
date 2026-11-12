#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Running all migrations ==="

bash "$ROOT_DIR/backend/clueterra-questions/scripts/migrate.sh"
bash "$ROOT_DIR/backend/clueterra-budget/scripts/migrate.sh"
bash "$ROOT_DIR/backend/clueterra-api/scripts/migrate.sh"

echo "=== All migrations complete ==="
