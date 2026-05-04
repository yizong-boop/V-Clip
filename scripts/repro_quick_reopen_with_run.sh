#!/usr/bin/env bash

set -euo pipefail

LOG_FILE="${LOG_FILE:-/tmp/vclip-repro-$(date +%Y%m%d-%H%M%S).log}"
APP_START_DELAY_MS="${APP_START_DELAY_MS:-2500}"

cleanup() {
  if [[ -n "${APP_PID:-}" ]]; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT

echo "[repro-suite] writing combined log to $LOG_FILE" >&2

(
  cd "$(dirname "$0")/.."
  ./gradlew :composeApp:run 2>&1
) | tee "$LOG_FILE" &
APP_PID=$!

python3 - "$APP_START_DELAY_MS" <<'PY'
import sys
import time
time.sleep(int(sys.argv[1]) / 1000.0)
PY

(
  cd "$(dirname "$0")/.."
  bash scripts/repro_quick_reopen.sh
) 2>&1 | tee -a "$LOG_FILE"

echo "[repro-suite] reproduction input sequence finished; stop the app when you've captured enough logs." >&2
wait "$APP_PID"
