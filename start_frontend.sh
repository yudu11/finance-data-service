#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

if [[ ! -d "$FRONTEND_DIR" ]]; then
  echo "Frontend directory not found at $FRONTEND_DIR" >&2
  exit 1
fi

API_BASE_URL="${VITE_API_BASE_URL:-http://localhost:8080}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-base)
      shift
      API_BASE_URL="$1"
      ;;
    --api-base=*)
      API_BASE_URL="${1#*=}"
      ;;
    --skip-install)
      SKIP_INSTALL=true
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Usage: $0 [--api-base <url>] [--skip-install]" >&2
      exit 1
      ;;
  esac
  shift
done

export VITE_API_BASE_URL="$API_BASE_URL"

if [[ -z "${SKIP_INSTALL:-}" && ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "Installing dependencies..."
  (cd "$FRONTEND_DIR" && npm install)
fi

echo "Starting frontend with API base $VITE_API_BASE_URL"
(cd "$FRONTEND_DIR" && npm run dev -- --host 0.0.0.0)
