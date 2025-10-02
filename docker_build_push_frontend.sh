#!/usr/bin/env bash
set -euo pipefail

DEFAULT_LOCAL_IMAGE_NAME="${DEFAULT_LOCAL_FRONTEND_IMAGE:-finance-frontend:local}"
DEFAULT_REMOTE_IMAGE_NAME="${DEFAULT_REMOTE_FRONTEND_IMAGE:-docker.io/dodo53456/finance-data-frontend:latest}"
DEFAULT_CONTAINER_NAME="${DEFAULT_FRONTEND_CONTAINER_NAME:-finance-frontend}"
DEFAULT_HOST_PORT="${DEFAULT_FRONTEND_HOST_PORT:-3000}"
DEFAULT_BACKEND_PORT="${DEFAULT_FRONTEND_BACKEND_PORT:-8080}"

usage() {
  cat <<USAGE
Usage: $0 [--local-image <name[:tag]>] [--remote-image <repository[:tag]>] \
          [--api-base <url>] [--backend-service <name>] [--backend-port <port>] \
          [--platform <value>] [--push-only] [--no-push-remote] \
          [--container-name <value>] [--host-port <port>] [--network <name>] [--no-run]

Options:
  --local-image     Local image tag to build (defaults to ${DEFAULT_LOCAL_IMAGE_NAME})
  --remote-image    Remote image tag for push (defaults to ${DEFAULT_REMOTE_IMAGE_NAME})
  --api-base        Base URL for backend API (defaults to "${VITE_API_BASE_URL:-http://localhost:8080}")
  --backend-service Docker DNS name of backend container (sets api-base to http://<name>:<port>)
  --backend-port    Port used with --backend-service (defaults to ${DEFAULT_BACKEND_PORT})
  --platform        Optional platform to target (e.g. linux/amd64)
  --push-only       Skip the build step and push the existing local image
  --no-push-remote  Disable pushing the image to the remote registry
  --container-name  Name for the local container (defaults to ${DEFAULT_CONTAINER_NAME})
  --host-port       Host port to expose the frontend (defaults to ${DEFAULT_HOST_PORT})
  --network         Optional Docker network to attach the container to
  --no-run          Skip running the container after build/push
  -h, --help        Show this help message

Environment:
  VITE_API_BASE_URL               Overrides backend API URL for build-time configuration
  DEFAULT_LOCAL_FRONTEND_IMAGE    Overrides default local image tag
  DEFAULT_REMOTE_FRONTEND_IMAGE   Overrides default remote image tag
  DEFAULT_FRONTEND_CONTAINER_NAME Overrides default container name when running locally
  DEFAULT_FRONTEND_HOST_PORT      Overrides default host port mapping
  DEFAULT_FRONTEND_BACKEND_PORT   Overrides default backend port when using --backend-service
USAGE
}

LOCAL_IMAGE_NAME="$DEFAULT_LOCAL_IMAGE_NAME"
REMOTE_IMAGE_NAME="$DEFAULT_REMOTE_IMAGE_NAME"
CONTAINER_NAME="$DEFAULT_CONTAINER_NAME"
HOST_PORT="$DEFAULT_HOST_PORT"
DOCKER_NETWORK=""
API_BASE="${VITE_API_BASE_URL:-http://localhost:8080}"
BACKEND_SERVICE=""
BACKEND_PORT="$DEFAULT_BACKEND_PORT"
PLATFORM=""
PUSH_ONLY=false
PUSH_REMOTE=true
RUN_CONTAINER=true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-image)
      LOCAL_IMAGE_NAME="${2:-}"
      shift 2
      ;;
    --remote-image)
      REMOTE_IMAGE_NAME="${2:-}"
      shift 2
      ;;
    --api-base)
      API_BASE="${2:-}"
      shift 2
      ;;
    --backend-service)
      BACKEND_SERVICE="${2:-}"
      shift 2
      ;;
    --backend-port)
      BACKEND_PORT="${2:-}"
      shift 2
      ;;
    --platform)
      PLATFORM="${2:-}"
      shift 2
      ;;
    --push-only)
      PUSH_ONLY=true
      shift
      ;;
    --no-push-remote)
      PUSH_REMOTE=false
      shift
      ;;
    --container-name)
      CONTAINER_NAME="${2:-}"
      shift 2
      ;;
    --host-port)
      HOST_PORT="${2:-}"
      shift 2
      ;;
    --network)
      DOCKER_NETWORK="${2:-}"
      shift 2
      ;;
    --no-run)
      RUN_CONTAINER=false
      shift
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

if [[ -n "$BACKEND_SERVICE" ]]; then
  API_BASE="http://${BACKEND_SERVICE}:${BACKEND_PORT}"
fi

if [[ -z "$LOCAL_IMAGE_NAME" ]]; then
  echo "Local image name cannot be empty" >&2
  usage >&2
  exit 1
fi

if [[ "$PUSH_REMOTE" == "true" && -z "$REMOTE_IMAGE_NAME" ]]; then
  echo "Remote image name cannot be empty when remote push is enabled" >&2
  usage >&2
  exit 1
fi

if [[ "$RUN_CONTAINER" == "true" ]]; then
  if [[ -z "$CONTAINER_NAME" ]]; then
    echo "Container name cannot be empty when run is enabled" >&2
    usage >&2
    exit 1
  fi

  if [[ -z "$HOST_PORT" ]]; then
    echo "Host port cannot be empty when run is enabled" >&2
    usage >&2
    exit 1
  fi
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

if [[ ! -d "$FRONTEND_DIR" ]]; then
  echo "Frontend directory not found at $FRONTEND_DIR" >&2
  exit 1
fi

echo "Using API base url: $API_BASE"

echo "Local image tag: $LOCAL_IMAGE_NAME"
if [[ "$PUSH_REMOTE" == "true" ]]; then
  echo "Remote image tag: $REMOTE_IMAGE_NAME"
else
  echo "Remote push disabled"
fi

if [[ "$PUSH_ONLY" != "true" ]]; then
  if [[ -n "$PLATFORM" ]]; then
    echo "Building image $LOCAL_IMAGE_NAME for platform $PLATFORM"
    docker buildx build \
      --platform "$PLATFORM" \
      -f "$FRONTEND_DIR/Dockerfile" \
      --build-arg "VITE_API_BASE_URL=$API_BASE" \
      -t "$LOCAL_IMAGE_NAME" \
      --load \
      "$FRONTEND_DIR"
  else
    echo "Building image $LOCAL_IMAGE_NAME"
    docker build \
      -f "$FRONTEND_DIR/Dockerfile" \
      --build-arg "VITE_API_BASE_URL=$API_BASE" \
      -t "$LOCAL_IMAGE_NAME" \
      "$FRONTEND_DIR"
  fi
else
  echo "Skipping build step (--push-only set)"
fi

if [[ "$PUSH_REMOTE" == "true" ]]; then
  echo "Tagging $LOCAL_IMAGE_NAME as $REMOTE_IMAGE_NAME for remote push"
  docker tag "$LOCAL_IMAGE_NAME" "$REMOTE_IMAGE_NAME"

  echo "Pushing image $REMOTE_IMAGE_NAME"
  docker push "$REMOTE_IMAGE_NAME"
else
  echo "Skipping remote push"
fi

if [[ "$RUN_CONTAINER" == "true" ]]; then
  if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
    echo "Container $CONTAINER_NAME already exists. Removing to recreate with new image"
    docker rm -f "$CONTAINER_NAME"
  fi

  echo "Starting container $CONTAINER_NAME from $LOCAL_IMAGE_NAME on host port $HOST_PORT"
  run_args=(-d --restart unless-stopped --name "$CONTAINER_NAME" -p "$HOST_PORT:80")

  if [[ -n "$DOCKER_NETWORK" ]]; then
    run_args+=(--network "$DOCKER_NETWORK")
  fi

  run_args+=("$LOCAL_IMAGE_NAME")

  docker run "${run_args[@]}"
else
  echo "Skipping container run"
fi
