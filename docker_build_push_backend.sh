#!/usr/bin/env bash
set -euo pipefail

# Configuration with sensible defaults; override via environment variables.
IMAGE_TAG="${IMAGE_TAG:-finance-data-service:latest}"
REMOTE_IMAGE="${REMOTE_IMAGE:-docker.io/dodo53456/finance-data-service:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-finance-data-service}"
ENV_FILE="${ENV_FILE:-}"
PUSH_IMAGE="${PUSH_IMAGE:-true}"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

DATA_DIR="${DATA_DIR:-${PROJECT_ROOT}/data}"
mkdir -p "$DATA_DIR"
printf '\n==> Using data directory %s (bind-mounted to /app/data)\n' "$DATA_DIR"

printf '\n==> Building Gradle project...\n'
./gradlew clean build

printf '\n==> Building Docker image %s...\n' "$IMAGE_TAG"
docker build -t "$IMAGE_TAG" .

if [[ "$PUSH_IMAGE" == "true" ]]; then
    printf '\n==> Tagging image as %s...\n' "$REMOTE_IMAGE"
    docker tag "$IMAGE_TAG" "$REMOTE_IMAGE"

    printf '\n==> Pushing image to registry...\n'
    docker push "$REMOTE_IMAGE"
else
    printf '\n==> Skipping push step (PUSH_IMAGE=%s).\n' "$PUSH_IMAGE"
fi

if docker ps -a --format '{{.Names}}' | grep -Eq "^${CONTAINER_NAME}$"; then
    printf '\n==> Removing existing container %s to apply the new image...\n' "$CONTAINER_NAME"
    docker rm -f "$CONTAINER_NAME"
fi

printf '\n==> Starting container %s from local image %s...\n' "$CONTAINER_NAME" "$IMAGE_TAG"
run_args=(-d --restart unless-stopped -p 8080:8080 --name "$CONTAINER_NAME")
run_args+=(-v "$DATA_DIR:/app/data")
if [[ -n "$ENV_FILE" ]]; then
    if [[ -f "$ENV_FILE" ]]; then
        run_args+=(--env-file "$ENV_FILE")
    else
        printf 'Warning: ENV_FILE %s does not exist; skipping.\n' "$ENV_FILE" >&2
    fi
fi

run_args+=("$IMAGE_TAG")

docker run "${run_args[@]}"

printf '\n==> Container %s is running with local image %s.\n' "$CONTAINER_NAME" "$IMAGE_TAG"
