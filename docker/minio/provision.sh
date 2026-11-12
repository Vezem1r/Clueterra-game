#!/bin/sh
set -eu

: "${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
: "${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
: "${MINIO_API_SECRET_KEY:?MINIO_API_SECRET_KEY is required}"
: "${MINIO_QUESTIONS_SECRET_KEY:?MINIO_QUESTIONS_SECRET_KEY is required}"
: "${MINIO_BUDGET_SECRET_KEY:?MINIO_BUDGET_SECRET_KEY is required}"

ALIAS="clueterra-provisioning"
POLICY_DIR="/opt/clueterra/minio/policies"
LIFECYCLE_DIR="/opt/clueterra/minio/lifecycle"
API_USER="clueterra-api"
QUESTIONS_USER="clueterra-questions"
BUDGET_USER="clueterra-budget"

mc alias set "$ALIAS" http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
trap 'mc alias remove "$ALIAS" >/dev/null 2>&1 || true' EXIT

mc mb --ignore-existing "$ALIAS/avatars" "$ALIAS/hint-images" "$ALIAS/budget-images"

mc anonymous set download "$ALIAS/avatars"
mc anonymous set none "$ALIAS/hint-images"
mc anonymous set none "$ALIAS/budget-images"
mc version enable "$ALIAS/budget-images"
mc ilm rule import "$ALIAS/budget-images" < "$LIFECYCLE_DIR/budget-images.json"

mc admin policy create "$ALIAS" clueterra-api-storage \
  "$POLICY_DIR/clueterra-api-storage.json"
mc admin policy create "$ALIAS" clueterra-questions-storage \
  "$POLICY_DIR/clueterra-questions-storage.json"
mc admin policy create "$ALIAS" clueterra-budget-storage \
  "$POLICY_DIR/clueterra-budget-storage.json"

mc admin user add "$ALIAS" "$API_USER" "$MINIO_API_SECRET_KEY"
mc admin user add "$ALIAS" "$QUESTIONS_USER" "$MINIO_QUESTIONS_SECRET_KEY"
mc admin user add "$ALIAS" "$BUDGET_USER" "$MINIO_BUDGET_SECRET_KEY"

mc admin policy attach "$ALIAS" clueterra-api-storage --user "$API_USER"
mc admin policy attach "$ALIAS" clueterra-questions-storage --user "$QUESTIONS_USER"
mc admin policy attach "$ALIAS" clueterra-budget-storage --user "$BUDGET_USER"

echo "MinIO buckets and least-privilege service identities are ready."
