#!/usr/bin/env bash
set -Eeuo pipefail

MESSAGE="${1:-Notificación de infraestructura}"
: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN requerido}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID requerido}"

curl --fail --silent --show-error \
  --request POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  --data-urlencode "chat_id=${TELEGRAM_CHAT_ID}" \
  --data-urlencode "parse_mode=HTML" \
  --data-urlencode "text=${MESSAGE}" >/dev/null
