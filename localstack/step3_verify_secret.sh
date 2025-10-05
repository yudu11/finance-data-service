#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
ACCESS_KEY="${AWS_ACCESS_KEY_ID:-test}"
SECRET_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
ALPHA_SECRET_NAME="${ALPHA_VANTAGE_SECRET_NAME:-finance/backend/alpha-vantage/api-key}"
TWELVE_SECRET_NAME="${TWELVE_DATA_SECRET_NAME:-finance/backend/twelve-data/api-key}"

if ! command -v aws >/dev/null 2>&1; then
    printf 'AWS CLI is required but not found. Install awscli first.\n' >&2
    exit 1
fi

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION="$REGION"

fetch_secret() {
    local secret_name="$1"
    printf 'Fetching secret %s from %s\n' "$secret_name" "$ENDPOINT"
    aws --endpoint-url "$ENDPOINT" secretsmanager get-secret-value \
        --secret-id "$secret_name"
}

fetch_secret "$ALPHA_SECRET_NAME"
fetch_secret "$TWELVE_SECRET_NAME"
