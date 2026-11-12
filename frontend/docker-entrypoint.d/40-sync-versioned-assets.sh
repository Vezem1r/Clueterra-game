#!/bin/sh
set -eu

readonly source_dir="/opt/clueterra/current-assets"
readonly target_dir="/usr/share/nginx/html/assets"
readonly retention_hours="${ASSET_RETENTION_HOURS:-72}"

case "$retention_hours" in
  ''|*[!0-9]*|0)
    echo "ASSET_RETENTION_HOURS must be a positive integer" >&2
    exit 1
    ;;
esac

if [ ! -d "$source_dir" ]; then
  echo "Current frontend assets are missing from $source_dir" >&2
  exit 1
fi

mkdir -p "$target_dir"

cp -R "$source_dir"/. "$target_dir"/

readonly retention_minutes=$((retention_hours * 60))
find "$target_dir" -type f -mmin "+$retention_minutes" -delete
