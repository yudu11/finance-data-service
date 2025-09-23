#!/usr/bin/env bash
set -euo pipefail

# Always run from the repository root so relative paths behave as expected.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Ensure the Gradle wrapper is executable before invoking it.
if [[ ! -x ./gradlew ]]; then
    chmod +x ./gradlew
fi

printf '\n==> Starting FinanceDataService via ./gradlew bootRun...\n\n'
exec ./gradlew bootRun "$@"
