#!/bin/sh
set -eu

if ! restic snapshots >/dev/null 2>&1; then
  restic init
fi

restic backup /source \
  --exclude '/source/.git' \
  --exclude '/source/backups/repository' \
  --exclude '/source/backups/cache' \
  --tag infra-platform

restic forget \
  --keep-daily 7 \
  --keep-weekly 4 \
  --keep-monthly 12 \
  --prune

restic check
