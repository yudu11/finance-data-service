#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: $0 --image <repository[:tag]> [--api-base <url>] [--platform <value>] [--push-only]

Options:
  --image         Fully qualified image name to build and push (required)
  --api-base      Base URL for backend API (defaults to \\\"${VITE_API_BASE_URL:-http://localhost:8080}\\\")
  --platform      Optional platform to target (e.g. linux/amd64)
  --push-only     Skip the build step and push the existing local image
  -h, --help      Show this help message

Environment:
  VITE_API_BASE_URL  Overrides backend API URL for build-time configuration
USAGE
}

IMAGE_NAME=""
API_BASE="${VITE_API_BASE_URL:-http://localhost:8080}"
PLATFORM=""
PUSH_ONLY=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)
      IMAGE_NAME="${2:-}"
      shift 2
      ;;
    --api-base)
      API_BASE="${2:-}"
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

if [[ -z "$IMAGE_NAME" ]]; then
  echo "--image is required" >&2
  usage >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

if [[ ! -d "$FRONTEND_DIR" ]]; then
  echo "Frontend directory not found at $FRONTEND_DIR" >&2
  exit 1
fi

echo "Using API base url: $API_BASE"

if [[ "$PUSH_ONLY" != "true" ]]; then
  if [[ -n "$PLATFORM" ]]; then
    echo "Building image $IMAGE_NAME for platform $PLATFORM"
    docker buildx build \
      --platform "$PLATFORM" \
      -f "$FRONTEND_DIR/Dockerfile" \
      --build-arg "VITE_API_BASE_URL=$API_BASE" \
      -t "$IMAGE_NAME" \
      --load \
      "$FRONTEND_DIR"
  else
    echo "Building image $IMAGE_NAME"
    docker build \
      -f "$FRONTEND_DIR/Dockerfile" \
      --build-arg "VITE_API_BASE_URL=$API_BASE" \
      -t "$IMAGE_NAME" \
      "$FRONTEND_DIR"
  fi
fi

echo "Pushing image $IMAGE_NAME"
docker push "$IMAGE_NAME"
