#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${LOCALSTACK_ENDPOINT:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"
ACCESS_KEY="${AWS_ACCESS_KEY_ID:-test}"
SECRET_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
ALPHA_SECRET_NAME="${ALPHA_VANTAGE_SECRET_NAME:-finance/backend/alpha-vantage/api-key}"
ALPHA_SECRET_DESC="${ALPHA_VANTAGE_SECRET_DESCRIPTION:-AlphaVantage API key}"
ALPHA_SECRET_FILE="${ALPHA_VANTAGE_SECRET_FILE:-}"
ALPHA_SECRET_VALUE="${ALPHA_VANTAGE_API_KEY:-}"
TWELVE_SECRET_NAME="${TWELVE_DATA_SECRET_NAME:-finance/backend/twelve-data/api-key}"
TWELVE_SECRET_DESC="${TWELVE_DATA_SECRET_DESCRIPTION:-Twelve Data API key}"
TWELVE_SECRET_FILE="${TWELVE_DATA_SECRET_FILE:-}"
TWELVE_SECRET_VALUE="${TWELVE_DATA_API_KEY:-}"

if ! command -v aws >/dev/null 2>&1; then
    printf 'AWS CLI is required but not found. Install awscli first.\n' >&2
    exit 1
fi

read_secret_value() {
    local file_path="$1"
    local inline_value="$2"
    local secret_label="$3"

    if [[ -n "$file_path" ]]; then
        if [[ ! -f "$file_path" ]]; then
            printf '%s secret file %s not found.\n' "$secret_label" "$file_path" >&2
            exit 1
        fi
        inline_value="$(<"$file_path")"
    fi

    inline_value="${inline_value//[$'\r\n']}"

    if [[ -z "${inline_value// }" ]]; then
        printf '%s secret value not provided. Set %s or %s.\n' "$secret_label" \
            "${secret_label^^}_SECRET_FILE" "${secret_label^^}_API_KEY" >&2
        exit 1
    fi

    printf '%s' "$inline_value"
}

seed_secret() {
    local secret_name="$1"
    local secret_desc="$2"
    local secret_value="$3"

    if aws --endpoint-url "$ENDPOINT" secretsmanager describe-secret --secret-id "$secret_name" >/dev/null 2>&1; then
        printf 'Secret %s exists. Updating value.\n' "$secret_name"
        aws --endpoint-url "$ENDPOINT" secretsmanager put-secret-value \
            --secret-id "$secret_name" \
            --secret-string "$secret_value" >/dev/null
    else
        printf 'Creating secret %s at %s\n' "$secret_name" "$ENDPOINT"
        aws --endpoint-url "$ENDPOINT" secretsmanager create-secret \
            --name "$secret_name" \
            --description "$secret_desc" \
            --secret-string "$secret_value" >/dev/null
    fi
}

export AWS_ACCESS_KEY_ID="$ACCESS_KEY"
export AWS_SECRET_ACCESS_KEY="$SECRET_KEY"
export AWS_DEFAULT_REGION="$REGION"

alpha_value="$(read_secret_value "$ALPHA_SECRET_FILE" "$ALPHA_SECRET_VALUE" "ALPHA_VANTAGE")"
twelve_value="$(read_secret_value "$TWELVE_SECRET_FILE" "$TWELVE_SECRET_VALUE" "TWELVE_DATA")"

seed_secret "$ALPHA_SECRET_NAME" "$ALPHA_SECRET_DESC" "$alpha_value"
seed_secret "$TWELVE_SECRET_NAME" "$TWELVE_SECRET_DESC" "$twelve_value"

printf 'Secrets %s and %s are ready.\n' "$ALPHA_SECRET_NAME" "$TWELVE_SECRET_NAME"
