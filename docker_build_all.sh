#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

NETWORK_NAME="finance-app"
BACKEND_CONTAINER_NAME="finance-data-service"
FRONTEND_CONTAINER_NAME="finance-frontend"
BACKEND_PORT=8080
FRONTEND_HOST_PORT=3000
PUSH_TO_REMOTE=true

usage() {
  cat <<USAGE
Usage: $0 [options]

Options:
  --frontend-port <port>      Host port to expose the frontend (default: 3000)
  --push-images <true|false>  Push backend/frontend images to remote (default: true)
  -h, --help                  Show this help message
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --frontend-port)
      FRONTEND_HOST_PORT="${2:-3000}"
      shift 2
      ;;
    --push-images)
      PUSH_TO_REMOTE="${2:-true}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

PUSH_TO_REMOTE_LOWER=$(printf '%s' "$PUSH_TO_REMOTE" | tr 'A-Z' 'a-z')
if [[ "$PUSH_TO_REMOTE_LOWER" != "true" && "$PUSH_TO_REMOTE_LOWER" != "false" ]]; then
  echo "--push-images must be true or false" >&2
  exit 1
fi

PUSH_TO_REMOTE="$PUSH_TO_REMOTE_LOWER"

if [[ -z "$FRONTEND_HOST_PORT" ]]; then
  echo "--frontend-port requires a value" >&2
  exit 1
fi

if ! [[ "$FRONTEND_HOST_PORT" =~ ^[0-9]+$ ]]; then
  echo "--frontend-port must be numeric" >&2
  exit 1
fi

if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
  echo "==> Creating docker network $NETWORK_NAME"
  docker network create "$NETWORK_NAME"
else
  echo "==> Docker network $NETWORK_NAME already exists"
fi

echo "\n==> Building and running backend"
PUSH_IMAGE="$PUSH_TO_REMOTE" \
CONTAINER_NAME="$BACKEND_CONTAINER_NAME" \
./docker_build_push_backend.sh

echo "==> Attaching backend container to network $NETWORK_NAME"
docker network connect "$NETWORK_NAME" "$BACKEND_CONTAINER_NAME" 2>/dev/null || true

echo "\n==> Building, pushing, and running frontend"
FRONTEND_CMD_ARGS=(
  "--api-base" "http://localhost:${BACKEND_PORT}"
  "--network" "$NETWORK_NAME"
  "--host-port" "$FRONTEND_HOST_PORT"
)
if [[ "$PUSH_TO_REMOTE" != "true" ]]; then
  FRONTEND_CMD_ARGS+=("--no-push-remote")
fi
DEFAULT_FRONTEND_CONTAINER_NAME="$FRONTEND_CONTAINER_NAME" \
DEFAULT_FRONTEND_HOST_PORT="$FRONTEND_HOST_PORT" \
DEFAULT_FRONTEND_BACKEND_PORT="$BACKEND_PORT" \
./docker_build_push_frontend.sh "${FRONTEND_CMD_ARGS[@]}"

# Determine the exposed frontend URL
PORT_MAPPING="$(docker port "$FRONTEND_CONTAINER_NAME" 80/tcp | head -n 1 || true)"
if [[ -z "$PORT_MAPPING" ]]; then
  ACCESS_PORT="$FRONTEND_HOST_PORT"
else
  ACCESS_PORT="${PORT_MAPPING##*:}"
fi

printf '\nFrontend available at: http://localhost:%s\n' "$ACCESS_PORT"
