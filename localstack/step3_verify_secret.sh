#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
ACCESS_KEY="${AWS_ACCESS_KEY_ID:-test}"
SECRET_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
SECRET_NAME="${LOCALSTACK_SECRET_NAME:-finance/backend/config}"

if ! command -v aws >/dev/null 2>&1; then
    printf 'AWS CLI is required but not found. Install awscli first.\n' >&2
    exit 1
fi

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION="$REGION"

printf 'Fetching secret %s from %s\n' "$SECRET_NAME" "$ENDPOINT"
aws --endpoint-url "$ENDPOINT" secretsmanager get-secret-value \
    --secret-id "$SECRET_NAME"
