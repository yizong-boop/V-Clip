#!/usr/bin/env bash

set -euo pipefail

ITERATIONS="${ITERATIONS:-1}"
SHOW_TO_ENTER_DELAY_MS="${SHOW_TO_ENTER_DELAY_MS:-120}"
ENTER_TO_REOPEN_DELAY_MS="${ENTER_TO_REOPEN_DELAY_MS:-35}"
BETWEEN_ITERATIONS_DELAY_MS="${BETWEEN_ITERATIONS_DELAY_MS:-900}"
INITIAL_DELAY_MS="${INITIAL_DELAY_MS:-250}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/repro_quick_reopen.sh

Environment variables:
  ITERATIONS                  Number of repro loops. Default: 1
  INITIAL_DELAY_MS            Delay before the first hotkey. Default: 250
  SHOW_TO_ENTER_DELAY_MS      Delay between Option+V and Enter. Default: 120
  ENTER_TO_REOPEN_DELAY_MS    Delay between Enter and the second Option+V. Default: 35
  BETWEEN_ITERATIONS_DELAY_MS Delay between loops. Default: 900

Example:
  ITERATIONS=10 ENTER_TO_REOPEN_DELAY_MS=20 ./scripts/repro_quick_reopen.sh
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! [[ "$ITERATIONS" =~ ^[0-9]+$ ]] || [[ "$ITERATIONS" -lt 1 ]]; then
  echo "ITERATIONS must be a positive integer." >&2
  exit 1
fi

for name in \
  INITIAL_DELAY_MS \
  SHOW_TO_ENTER_DELAY_MS \
  ENTER_TO_REOPEN_DELAY_MS \
  BETWEEN_ITERATIONS_DELAY_MS; do
  value="${!name}"
  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    echo "$name must be a non-negative integer number of milliseconds." >&2
    exit 1
  fi
done

timestamp() {
  python3 - <<'PY'
import datetime
print(datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3])
PY
}

log() {
  printf '[repro-hotkey] %s %s\n' "$(timestamp)" "$*" >&2
}

sleep_ms() {
  local millis="$1"
  python3 - "$millis" <<'PY'
import sys
import time

time.sleep(int(sys.argv[1]) / 1000.0)
PY
}

press_option_v() {
  osascript -e 'tell application "System Events" to key code 9 using option down'
}

press_enter() {
  osascript -e 'tell application "System Events" to key code 36'
}

run_iteration() {
  local iteration="$1"

  log "iteration=$iteration waiting ${INITIAL_DELAY_MS}ms before first Option+V"
  sleep_ms "$INITIAL_DELAY_MS"

  log "iteration=$iteration press Option+V"
  press_option_v

  log "iteration=$iteration waiting ${SHOW_TO_ENTER_DELAY_MS}ms before Enter"
  sleep_ms "$SHOW_TO_ENTER_DELAY_MS"

  log "iteration=$iteration press Enter"
  press_enter

  log "iteration=$iteration waiting ${ENTER_TO_REOPEN_DELAY_MS}ms before second Option+V"
  sleep_ms "$ENTER_TO_REOPEN_DELAY_MS"

  log "iteration=$iteration press Option+V again"
  press_option_v
}

log "starting repro with ITERATIONS=$ITERATIONS SHOW_TO_ENTER_DELAY_MS=$SHOW_TO_ENTER_DELAY_MS ENTER_TO_REOPEN_DELAY_MS=$ENTER_TO_REOPEN_DELAY_MS"

for ((i = 1; i <= ITERATIONS; i++)); do
  run_iteration "$i"

  if (( i < ITERATIONS )); then
    log "iteration=$i completed; sleeping ${BETWEEN_ITERATIONS_DELAY_MS}ms before next loop"
    sleep_ms "$BETWEEN_ITERATIONS_DELAY_MS"
  fi
done

log "repro completed"
