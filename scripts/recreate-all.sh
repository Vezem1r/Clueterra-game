#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

echo "=== Recreating all schemas ==="

bash "$ROOT_DIR/backend/clueterra-questions/scripts/recreate-db.sh"
bash "$ROOT_DIR/backend/clueterra-budget/scripts/recreate-db.sh"
bash "$ROOT_DIR/backend/clueterra-api/scripts/recreate-db.sh"

echo "=== All schemas recreated ==="
