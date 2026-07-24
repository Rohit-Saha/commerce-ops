#!/usr/bin/env bash
#
# Starts commerce-ops backend services as detached fat-jar processes.
# Logs: logs/<service>.log · PIDs: logs/pids/<service>.pid
#
# Prerequisites (or use --infra / --build):
#   docker compose up -d
#   mvn -q -DskipTests package
#
# Usage:
#   scripts/start-services.sh
#   scripts/start-services.sh --infra              # docker compose up -d + wait
#   scripts/start-services.sh --oidc               # Auth BFF (Keycloak via gateway)
#   scripts/start-services.sh --infra --oidc --build
#   scripts/start-services.sh order-service api-gateway
#   scripts/start-services.sh --help
#
# Env (also loaded from .env.local if present):
#   COMMERCE_SECURITY_MODE=legacy|oidc
#   SPRING_PROFILES_ACTIVE   (oidc profile when --oidc / mode=oidc)
#   OAUTH2_ISSUER_URI        (default http://localhost:8180/realms/commerce-ops)
#   KEYCLOAK_ADMIN_UI_BFF_SECRET / KEYCLOAK_STOREFRONT_BFF_SECRET
#   BFF_ADMIN_FRONTEND_URL / BFF_STOREFRONT_FRONTEND_URL
#   CONFIG_SERVER_URL / CONFIG_REPO_PATH
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

WITH_INFRA=0
WITH_BUILD=0
WITH_OIDC=0
SHOW_HELP=0
SERVICE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --infra|-i) WITH_INFRA=1 ;;
    --build|-b) WITH_BUILD=1 ;;
    --oidc|-o) WITH_OIDC=1 ;;
    --help|-h) SHOW_HELP=1 ;;
    --)
      shift
      SERVICE_ARGS+=("$@")
      break
      ;;
    -*)
      echo "Unknown option: $1 (try --help)" >&2
      exit 1
      ;;
    *)
      SERVICE_ARGS+=("$1")
      ;;
  esac
  shift
done

if [[ "$SHOW_HELP" -eq 1 ]]; then
  cat <<'EOF'
Starts commerce-ops backend jars (logs/ + logs/pids/).

  scripts/start-services.sh
  scripts/start-services.sh --infra              # docker compose up -d + wait
  scripts/start-services.sh --oidc               # Auth BFF (Keycloak via gateway)
  scripts/start-services.sh --infra --oidc --build
  scripts/start-services.sh order-service api-gateway

Flags: --infra|-i  --oidc|-o  --build|-b  --help|-h
Env:   COMMERCE_SECURITY_MODE, OAUTH2_ISSUER_URI, KEYCLOAK_*_BFF_SECRET,
       BFF_*_FRONTEND_URL, CONFIG_SERVER_URL (also from .env.local)
EOF
  exit 0
fi

# Optional local secrets (gitignored). Example: PAYMENT_PROVIDER / RAZORPAY_*
if [[ -f "$ROOT_DIR/.env.local" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env.local"
  set +a
  echo "commerce-ops :: loaded .env.local"
fi

# Treat COMMERCE_SECURITY_MODE=oidc from env/.env.local the same as --oidc
if [[ "${COMMERCE_SECURITY_MODE:-legacy}" == "oidc" ]]; then
  WITH_OIDC=1
fi

if [[ "$WITH_OIDC" -eq 1 ]]; then
  export COMMERCE_SECURITY_MODE=oidc
  if [[ -z "${SPRING_PROFILES_ACTIVE:-}" ]]; then
    export SPRING_PROFILES_ACTIVE=oidc
  elif [[ ",${SPRING_PROFILES_ACTIVE}," != *",oidc,"* ]]; then
    export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE},oidc"
  fi
  export OAUTH2_ISSUER_URI="${OAUTH2_ISSUER_URI:-http://localhost:8180/realms/commerce-ops}"
  export KEYCLOAK_ADMIN_UI_BFF_SECRET="${KEYCLOAK_ADMIN_UI_BFF_SECRET:-admin-ui-bff-secret}"
  export KEYCLOAK_STOREFRONT_BFF_SECRET="${KEYCLOAK_STOREFRONT_BFF_SECRET:-storefront-bff-secret}"
  export BFF_ADMIN_FRONTEND_URL="${BFF_ADMIN_FRONTEND_URL:-http://localhost:5173}"
  export BFF_STOREFRONT_FRONTEND_URL="${BFF_STOREFRONT_FRONTEND_URL:-http://localhost:5174}"
  echo "commerce-ops :: Auth BFF / OIDC mode"
  echo "  COMMERCE_SECURITY_MODE=$COMMERCE_SECURITY_MODE"
  echo "  SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
  echo "  OAUTH2_ISSUER_URI=$OAUTH2_ISSUER_URI"
fi

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(1.5)
try:
    s.connect((host, port))
    sys.exit(0)
except Exception:
    sys.exit(1)
finally:
    s.close()
PY
}

wait_tcp() {
  local host="$1" port="$2" label="$3" attempts="${4:-60}"
  local i=0
  echo -n "commerce-ops :: waiting for $label ($host:$port)"
  while (( i < attempts )); do
    if port_open "$host" "$port"; then
      echo " — ready"
      return 0
    fi
    echo -n "."
    sleep 2
    i=$((i + 1))
  done
  echo
  echo "commerce-ops :: timed out waiting for $label" >&2
  return 1
}

ensure_infra() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "commerce-ops :: docker not found; cannot start infra" >&2
    exit 1
  fi
  echo "commerce-ops :: docker compose up -d"
  docker compose up -d

  wait_tcp localhost 5433 "Postgres" 45
  wait_tcp localhost 6379 "Redis" 30
  wait_tcp localhost 9092 "Kafka" 60

  if [[ "$WITH_OIDC" -eq 1 ]]; then
    wait_tcp localhost 8180 "Keycloak" 90
    echo "commerce-ops :: Keycloak realm: deploy/keycloak/commerce-ops-realm.json"
    echo "  (If BFF clients are missing, recreate the keycloak volume and re-import.)"
  fi
}

ensure_oidc_deps() {
  if [[ "$WITH_INFRA" -eq 1 ]]; then
    return 0
  fi
  echo "commerce-ops :: checking OIDC deps (redis + keycloak)"
  if ! wait_tcp localhost 6379 "Redis" 5; then
    echo "  hint: scripts/start-services.sh --infra --oidc" >&2
    exit 1
  fi
  if ! wait_tcp localhost 8180 "Keycloak" 5; then
    echo "  hint: docker compose up -d keycloak   # or --infra --oidc" >&2
    exit 1
  fi
}

if [[ "$WITH_INFRA" -eq 1 ]]; then
  ensure_infra
elif [[ "$WITH_OIDC" -eq 1 ]]; then
  ensure_oidc_deps
fi

if [[ "$WITH_BUILD" -eq 1 ]]; then
  echo "commerce-ops :: mvn -q -DskipTests package"
  mvn -q -DskipTests package
fi

export COMMERCE_OPS_ROOT="$ROOT_DIR"
export COMMERCE_OPS_FILTER="${SERVICE_ARGS[*]:-}"
export CONFIG_REPO_PATH="${CONFIG_REPO_PATH:-$ROOT_DIR/config-repo}"
export CONFIG_SERVER_URL="${CONFIG_SERVER_URL:-http://localhost:8888}"
export CONFIG_SERVER_SEARCH_LOCATIONS="${CONFIG_SERVER_SEARCH_LOCATIONS:-file:${CONFIG_REPO_PATH}/,classpath:/config-repo/}"

python3 << 'PY'
import os, subprocess, time
from pathlib import Path

root = Path(os.environ["COMMERCE_OPS_ROOT"])
wanted = {x for x in os.environ.get("COMMERCE_OPS_FILTER", "").split() if x}
log_dir = root / "logs"
pid_dir = log_dir / "pids"
log_dir.mkdir(exist_ok=True)
pid_dir.mkdir(exist_ok=True)

# config-server first so optional clients can import when present
services = [
    ("config-server", "services/config-server", 8888),
    ("order-service", "services/order-service", 8081),
    ("inventory-service", "services/inventory-service", 8082),
    ("payment-service", "services/payment-service", 8083),
    ("shipping-service", "services/shipping-service", 8084),
    ("saga-orchestrator", "services/saga-orchestrator", 8085),
    ("returns-service", "services/returns-service", 8086),
    ("customer-service", "services/customer-service", 8087),
    ("catalog-service", "services/catalog-service", 8088),
    ("invoice-service", "services/invoice-service", 8089),
    ("api-gateway", "services/api-gateway", 8080),
]

mode = os.environ.get("COMMERCE_SECURITY_MODE", "legacy")
print(f"commerce-ops :: starting services (logs in {log_dir})")
print(f"commerce-ops :: security.mode={mode}")
print(f"commerce-ops :: CONFIG_REPO_PATH={os.environ.get('CONFIG_REPO_PATH')}")
print(f"commerce-ops :: CONFIG_SERVER_URL={os.environ.get('CONFIG_SERVER_URL')}\n")

env = os.environ.copy()
started = 0
skipped = 0

for name, module, port in services:
    if wanted and name not in wanted:
        continue
    pid_file = pid_dir / f"{name}.pid"
    if pid_file.exists():
        try:
            os.kill(int(pid_file.read_text().strip()), 0)
            print(f"  [skip] {name} -- already running (pid {pid_file.read_text().strip()})")
            skipped += 1
            continue
        except OSError:
            pass
    jars = [p for p in (root / module / "target").glob("*.jar") if not p.name.endswith(".original")]
    if not jars:
        print(f"  [skip] {name} -- no jar (run 'mvn -q -DskipTests package' or --build)")
        skipped += 1
        continue
    jar = jars[0]
    log = open(log_dir / f"{name}.log", "w")
    proc = subprocess.Popen(
        ["java", "-jar", str(jar), f"--server.port={port}"],
        stdout=log,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
        cwd=str(root),
        start_new_session=True,
        env=env,
    )
    pid_file.write_text(str(proc.pid))
    print(f"  [start] {name} -> port {port}, jar {jar.name}, pid {proc.pid}")
    started += 1
    if name == "config-server":
        time.sleep(2)

print(f"\nDone. started={started} skipped={skipped}")
print("Tail a log with:  tail -f logs/<service>.log")
print("Inspect config:   curl -s http://localhost:8888/api-gateway/default | head")
print("Stop jars with:   scripts/stop-services.sh")
print("Stop jars+infra:  scripts/stop-services.sh --infra")
if mode == "oidc":
    print("\nAuth BFF (OIDC):")
    print("  Login:      http://localhost:8080/api/auth/login?client=admin-ui")
    print("  Admin UI:   cd admin-ui && VITE_SECURITY_MODE=oidc npm run dev")
    print("  Storefront: cd storefront && VITE_SECURITY_MODE=oidc npm run dev")
    print("  Browser must not call Keycloak (:8180); only the gateway does.")
else:
    print("\nFrontends (legacy API key):")
    print("  Admin UI:   cd admin-ui && npm run dev")
    print("  Storefront: cd storefront && npm run dev")
    print("  OIDC/BFF:   scripts/start-services.sh --infra --oidc")
PY
