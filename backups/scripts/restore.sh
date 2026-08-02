#!/bin/sh
set -eu

SNAPSHOT="${1:-latest}"
TARGET="${2:-/restore}"

mkdir -p "$TARGET"
restic snapshots
restic restore "$SNAPSHOT" --target "$TARGET"

echo "Restauración completada en: $TARGET"
