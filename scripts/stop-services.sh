#!/usr/bin/env bash
#
# Stops services previously started by scripts/start-services.sh (pid files
# under logs/pids/). Optionally stops Docker Compose infra.
#
# Usage:
#   scripts/stop-services.sh              # stop JVM services only
#   scripts/stop-services.sh --infra      # also docker compose stop
#   scripts/stop-services.sh --all        # jars + docker compose down (keep volumes)
#   scripts/stop-services.sh --help
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_DIR="$ROOT_DIR/logs/pids"

WITH_INFRA=0
WITH_ALL=0
SHOW_HELP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --infra|-i) WITH_INFRA=1 ;;
    --all|-a) WITH_ALL=1; WITH_INFRA=1 ;;
    --help|-h) SHOW_HELP=1 ;;
    -*)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
    *)
      echo "Unexpected argument: $1 (try --help)" >&2
      exit 1
      ;;
  esac
  shift
done

if [[ "$SHOW_HELP" -eq 1 ]]; then
  cat <<'EOF'
Stops JVM services started by start-services.sh.

  scripts/stop-services.sh           # jars only
  scripts/stop-services.sh --infra   # also docker compose stop
  scripts/stop-services.sh --all     # jars + docker compose down (volumes kept)
EOF
  exit 0
fi

stopped=0
if [[ -d "$PID_DIR" ]]; then
  shopt -s nullglob
  for pid_file in "$PID_DIR"/*.pid; do
    name="$(basename "$pid_file" .pid)"
    pid="$(cat "$pid_file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "  [stop] $name (pid $pid)"
      kill "$pid" 2>/dev/null || true
      # Give the process a moment, then force if needed
      for _ in 1 2 3 4 5; do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.4
      done
      if kill -0 "$pid" 2>/dev/null; then
        echo "  [kill] $name (pid $pid) — SIGKILL"
        kill -9 "$pid" 2>/dev/null || true
      fi
      stopped=$((stopped + 1))
    else
      echo "  [skip] $name -- pid $pid not running"
    fi
    rm -f "$pid_file"
  done
  shopt -u nullglob
else
  echo "No pid files found under $PID_DIR -- no JVM services to stop."
fi

if [[ "$WITH_INFRA" -eq 1 ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "commerce-ops :: docker not found; skipped infra stop" >&2
  else
    cd "$ROOT_DIR"
    if [[ "$WITH_ALL" -eq 1 ]]; then
      echo "commerce-ops :: docker compose down (volumes kept)"
      docker compose down
    else
      echo "commerce-ops :: docker compose stop"
      docker compose stop
    fi
  fi
fi

echo "Done. (jvm stopped=$stopped)"
if [[ "$WITH_INFRA" -eq 0 ]]; then
  echo "Infra still running. Stop it with: scripts/stop-services.sh --infra"
fi
