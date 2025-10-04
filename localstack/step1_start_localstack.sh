#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"

if ! command -v docker >/dev/null 2>&1; then
    printf 'Docker is required but not found. Install Docker first.\n' >&2
    exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
    printf 'Compose file %s not found.\n' "$COMPOSE_FILE" >&2
    exit 1
fi

if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD=(docker-compose)
else
    printf 'Docker Compose is required (docker compose or docker-compose).\n' >&2
    exit 1
fi

export LOCALSTACK_CONTAINER_NAME="${LOCALSTACK_CONTAINER_NAME:-localstack-main}"
export LOCALSTACK_IMAGE="${LOCALSTACK_IMAGE:-localstack/localstack:latest}"
export LOCALSTACK_NETWORK="${LOCALSTACK_NETWORK:-localstack-net}"
export LOCALSTACK_NETWORK_EXTERNAL="${LOCALSTACK_NETWORK_EXTERNAL:-false}"
export LOCALSTACK_SERVICES="${LOCALSTACK_SERVICES:-secretsmanager}"
export LOCALSTACK_EDGE_PORT="${LOCALSTACK_EDGE_PORT:-4566}"
export LOCALSTACK_DEBUG="${LOCALSTACK_DEBUG:-0}"
export LOCALSTACK_VOLUME_NAME="${LOCALSTACK_VOLUME_NAME:-localstack-data}"
export AWS_REGION="${AWS_REGION:-us-east-1}"

if [[ "$LOCALSTACK_NETWORK_EXTERNAL" == "true" ]]; then
    if ! docker network inspect "$LOCALSTACK_NETWORK" >/dev/null 2>&1; then
        printf 'Creating external Docker network %s\n' "$LOCALSTACK_NETWORK"
        docker network create "$LOCALSTACK_NETWORK"
    fi
fi

PROJECT_NAME="${LOCALSTACK_COMPOSE_PROJECT:-localstack}"

printf 'Starting LocalStack via docker compose (project %s, container %s)\n' \
    "$PROJECT_NAME" "$LOCALSTACK_CONTAINER_NAME"

"${COMPOSE_CMD[@]}" \
    --project-name "$PROJECT_NAME" \
    -f "$COMPOSE_FILE" up -d --remove-orphans

printf 'LocalStack is starting. Tail logs with: %s --project-name %s logs -f localstack\n' \
    "${COMPOSE_CMD[*]}" "$PROJECT_NAME"
