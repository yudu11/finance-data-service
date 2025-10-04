#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
ACCESS_KEY="${AWS_ACCESS_KEY_ID:-test}"
SECRET_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
SECRET_NAME="${LOCALSTACK_SECRET_NAME:-finance/backend/config}"
SECRET_DESC="${LOCALSTACK_SECRET_DESCRIPTION:-Finance backend config for dev}" 
SECRET_FILE="${LOCALSTACK_SECRET_FILE:-}"
SECRET_STRING="${LOCALSTACK_SECRET_STRING:-}" 

if ! command -v aws >/dev/null 2>&1; then
    printf 'AWS CLI is required but not found. Install awscli first.\n' >&2
    exit 1
fi

if [[ -n "$SECRET_FILE" ]]; then
    if [[ ! -f "$SECRET_FILE" ]]; then
        printf 'Secret file %s not found.\n' "$SECRET_FILE" >&2
        exit 1
    fi
    SECRET_STRING="$(<"$SECRET_FILE")"
fi

if [[ -z "$SECRET_STRING" ]]; then
    SECRET_STRING='{"testKey":"testValue"}'
fi

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION="$REGION"

if aws --endpoint-url "$ENDPOINT" secretsmanager describe-secret --secret-id "$SECRET_NAME" >/dev/null 2>&1; then
    printf 'Secret %s already exists. Updating value.\n' "$SECRET_NAME"
    aws --endpoint-url "$ENDPOINT" secretsmanager put-secret-value \
        --secret-id "$SECRET_NAME" \
        --secret-string "$SECRET_STRING" >/dev/null
else
    printf 'Creating secret %s at %s\n' "$SECRET_NAME" "$ENDPOINT"
    aws --endpoint-url "$ENDPOINT" secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --description "$SECRET_DESC" \
        --secret-string "$SECRET_STRING" >/dev/null
fi

printf 'Secret %s is ready.\n' "$SECRET_NAME"
