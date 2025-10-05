# FinanceDataService

FinanceDataService is a Spring Boot application that collects gold and stock price data on startup, stores it locally as JSON snapshots, and exposes a REST endpoint to query historical prices.

## Project Structure
- `build.gradle.kts` – Gradle build configuration using Spring Boot 3 and Java 17.
- `src/main/java/com/example/financedataservice` – Application code organized into configuration, client, service, controller, model, and bootstrap packages.
- `src/main/resources/config/stocks.json` – Default stock symbols and lookback days for Twelve Data integration.
- `data/` – Per-symbol JSON histories persisted on disk (`{SYMBOL}.json`).
- `frontend/` – React + Vite single-page app for symbol selection and price charting.

## Running Locally
1. Ensure JDK 17+ and Gradle or the Gradle Wrapper (`./gradlew`) are available.
2. (Optional) Update `src/main/resources/config/stocks.json` with desired symbols.
3. Provide API keys via AWS Secrets Manager (LocalStack in development or AWS in other environments), or configure a local profile override (see [Local Profile Fallback](#local-profile-fallback)).
   To seed LocalStack with the sample key files bundled in this repo:
   ```bash
   ALPHA_VANTAGE_SECRET_FILE=localstack/sample-alpha-vantage-key.txt \
   TWELVE_DATA_SECRET_FILE=localstack/sample-twelve-data-key.txt \
   ./localstack/step2_seed_secret.sh
   ```
4. Run the application (default profile):
   ```bash
   ./gradlew bootRun
   ```
   Or run with the local fallback profile: 
   ```bash
   SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
   ```
   On startup the app downloads gold and stock data for the current day (if not already stored). The AlphaVantage and Twelve Data clients attempt to resolve API keys from Secrets Manager, falling back to local properties when necessary.

### Local Profile Fallback
For development without AWS/LocalStack, create `src/main/resources/application-local.yml` (git-ignored) with API keys and run the backend under the `local` Spring profile:
```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

If you prefer the wrapper script, set the profile before invoking it:
```bash
SPRING_PROFILES_ACTIVE=local ./start_app.sh
```

Example contents:
```yaml
alpha-vantage:
  api-key: W0I6NZZKXFK8R7V2

twelve-data:
  api-key: 58e87b1c442a464f8c10e61a77db7871
```
The application only reads these values when Secrets Manager is disabled or unavailable.

## Frontend Application
The repository hosts a React + TypeScript interface (Vite) that lets you select one or more symbols (including gold) and explore the historical price series with an interactive chart.

### Key Capabilities
- Multi-select landing page that loads available symbols from the `/symbols` endpoint
- Optional date-range filter passed through to the chart view
- ApexCharts-powered visualization with zooming, brushing, and shared OHLC tooltips across symbols

### Local Development
1. Ensure Node.js 18+ and npm are available.
2. Review `frontend/.env.example` and copy it to `.env` if you need to override the default backend URL.
3. Start the dev server (installs dependencies on first run):
   ```bash
   ./start_frontend.sh --api-base http://localhost:8080
   ```
   The UI is available at http://localhost:5173. Pass `--skip-install` to skip the automatic `npm install` step.

You can also work directly inside `frontend/`:
```bash
cd frontend
npm install
npm run dev -- --host
```

### Building the Frontend Bundle
```bash
cd frontend
npm install
npm run build
```
The production assets emit to `frontend/dist`.

### Docker Image
Build, push, and run the Nginx-hosted SPA with the helper script. By default it creates a
local image tagged `finance-frontend:local`, publishes to
`docker.io/dodo53456/finance-data-frontend:latest`, and runs a container named
`finance-frontend` exposed on `http://localhost:3000`.

Typical workflow when both backend and frontend run as containers:
```bash
docker network create finance-app || true
./docker_build_push_backend.sh                             # build + run backend (attach to finance-app afterwards)
docker network connect finance-app finance-data-service || true
./docker_build_push_frontend.sh --backend-service finance-data-service --network finance-app
```
- `--backend-service` rewrites the bundle to call `http://<service>:<port>` (port defaults to 8080).
- `--api-base` can still be used directly for non-Docker endpoints (e.g. production URLs).
- Disable registry pushes with `--no-push-remote`; skip running the container with `--no-run`.
- Access the UI at `http://localhost:3000` (or supply `--host-port` for a different mapping).

## Docker Usage
### Prerequisites
- Docker Desktop (or Docker Engine) is running locally.
- (Optional) Logged in to your Docker registry (`docker login`).

### Build, Push & Run via Script
Use the helper script to build the JAR, create the Docker image, optionally push it, and start the local container:
```bash
./docker_build_push_backend.sh
```
Environment variables:
- `IMAGE_TAG` (default `finance-data-service:latest`)
- `REMOTE_IMAGE` (default `docker.io/username/finance-data-service:latest`)
- `CONTAINER_NAME` (default `finance-data-service`)
- `ENV_FILE` path to load environment variables into the container
- `PUSH_IMAGE` (`true` by default). Set to `false` to skip pushing while still running the local image.
- `DATA_DIR` host folder to bind mount at `/app/data` (default `${PWD}/data`).
- `ALPHA_VANTAGE_API_KEY` and `TWELVE_DATA_API_KEY` optional fallback values that the script now injects into `docker run` if set.

Example without pushing to a registry:
```bash
PUSH_IMAGE=false ./docker_build_push_backend.sh
```

To supply fallback API keys via environment variables:
```bash
export ALPHA_VANTAGE_API_KEY=...
export TWELVE_DATA_API_KEY=...
./docker_build_push_backend.sh
```
The script forwards those values to `docker run` so the container can still authenticate if Secrets Manager is unavailable.

### Manual Docker Commands
```bash
# Build local image
docker build -t finance-data-service:latest .

# Push to registry
docker tag finance-data-service:latest docker.io/username/finance-data-service:latest
docker push docker.io/username/finance-data-service:latest

# Run locally (persisting data under ./data)
mkdir -p data
docker run -d -p 8080:8080 --restart unless-stopped \
  -v "$(pwd)/data:/app/data" \
  -e FINANCE_DATA_BASE_DIR=/app/data \
  --name finance-data-service \
  finance-data-service:latest
```

## k3d + Helm Deployment
### Prerequisites
- Docker Desktop (or Docker Engine)
- k3d, kubectl, and Helm installed locally

### Build the application image
Use a project-local Gradle cache to avoid permission issues while downloading the wrapper:
```bash
GRADLE_USER_HOME=$PWD/.gradle ./gradlew clean build
docker build -t finance-data-service:latest .
```

### Create the k3d cluster
Pick the access pattern that fits your workflow and create the cluster accordingly.

**Option 1 – Port-forward access (minimal cluster command):**
```bash
k3d cluster create finance --agents 2 --api-port 6443
```
This keeps the cluster internal; you’ll use `kubectl port-forward` later when you need host access.

**Option 2 – Direct NodePort access:**
```bash
k3d cluster create finance --agents 2 --api-port 6443 \
  --port 8080:30080@loadbalancer --port 30080:30080@loadbalancer
```
Here k3d publishes NodePort 30080 (and 8080 for convenience) to the host so your browser can reach the service without tunneling.

Import the freshly built image so the cluster can pull it without a registry:
```bash
k3d image import finance-data-service:latest -c finance
```

Confirm connectivity:
```bash
kubectl config current-context    # should show k3d-finance
kubectl get nodes                 # all nodes should be Ready
```

### Deploy with Helm
Validate the chart (optional) and install/upgrade the release:
```bash
helm lint charts/finance-data-service
helm upgrade --install finance charts/finance-data-service -n finance --create-namespace
```

Check the rollout and service details:
```bash
kubectl get pods -n finance
kubectl get svc -n finance
```

If you need a packaged chart tarball for distribution:
```bash
helm package charts/finance-data-service
```

### Run the application inside k3d
If you created the cluster with Option 1, forward the service port whenever you want host access:
```bash
kubectl port-forward svc/finance-finance-data-service 8080:8080 -n finance
# In a second terminal
curl "http://localhost:8080/getPriceData?symbol=AAPL"
```

If you created the cluster with Option 2, hit the published NodePort directly:
```bash
curl "http://localhost:30080/getPriceData?symbol=AAPL"
```

Independent of the option, you can always verify networking from within the k3d Docker network without port forwarding:
```bash
docker run --rm --network k3d-finance curlimages/curl \
  curl -sS 'http://k3d-finance-server-0:30080/getPriceData?symbol=AAPL'
```

### Cleanup
When you're finished, remove the release and the cluster:
```bash
helm uninstall finance -n finance
k3d cluster delete finance
```

## LocalStack Secrets Manager Scripts
Three helper scripts under `localstack/` walk through bringing up LocalStack with Secrets Manager, seeding required secrets, and verifying access. They default to the typical LocalStack credentials (`test` / `test`) and edge port `4566`, but you can override any setting via environment variables before running a script.

1. Start or restart the container (uses `docker compose` under the hood):
   ```bash
   ./localstack/step1_start_localstack.sh
   ```
   - Overrides: `LOCALSTACK_CONTAINER_NAME`, `LOCALSTACK_IMAGE`, `LOCALSTACK_NETWORK`, `LOCALSTACK_NETWORK_EXTERNAL`, `LOCALSTACK_SERVICES`, `LOCALSTACK_EDGE_PORT`, `LOCALSTACK_DEBUG`, `LOCALSTACK_VOLUME_NAME`, `LOCALSTACK_COMPOSE_PROJECT`, `AWS_REGION`.
   - Example (attach to k3d network as an external bridge that will be created if missing):
     ```bash
     LOCALSTACK_NETWORK=k3d-finance \
     LOCALSTACK_NETWORK_EXTERNAL=true \
     ./localstack/step1_start_localstack.sh
     ```

2. Create or update the AlphaVantage and Twelve Data secrets:
   ```bash
   ./localstack/step2_seed_secret.sh
   ```
   - Overrides: `ALPHA_VANTAGE_SECRET_NAME`, `ALPHA_VANTAGE_SECRET_DESCRIPTION`, `ALPHA_VANTAGE_API_KEY`, `ALPHA_VANTAGE_SECRET_FILE`, `TWELVE_DATA_SECRET_NAME`, `TWELVE_DATA_SECRET_DESCRIPTION`, `TWELVE_DATA_API_KEY`, `TWELVE_DATA_SECRET_FILE`, plus `LOCALSTACK_ENDPOINT`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
   - Example (load each key from a local file):
     ```bash
     ALPHA_VANTAGE_SECRET_FILE=localstack/sample-alpha-vantage-key.txt \
     TWELVE_DATA_SECRET_FILE=localstack/sample-twelve-data-key.txt \
     ./localstack/step2_seed_secret.sh
     ```

3. Retrieve secrets to confirm connectivity:
   ```bash
   ./localstack/step3_verify_secret.sh
   ```
   - Overrides: `ALPHA_VANTAGE_SECRET_NAME`, `TWELVE_DATA_SECRET_NAME`, `LOCALSTACK_ENDPOINT`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
   - Example (custom endpoint while running inside k3d):
     ```bash
     LOCALSTACK_ENDPOINT=http://host.k3d.internal:4566 ./localstack/step3_verify_secret.sh
     ```

The scripts rely on the AWS CLI and Docker. Install them first (`brew install awscli` on macOS, or download from the official installers) and ensure the Docker daemon is running.

## API Usage
Retrieve historical prices for a symbol (case-insensitive):
```bash
curl "http://localhost:8080/getPriceData?symbol=AAPL"
```
- Returns JSON array combining all stored snapshots for the symbol.
- Use `symbol=GOLD` for gold prices (stored as `XAUUSD`).

## Testing
Run all tests:
```bash
./gradlew test
```
The suite covers configuration loading, HTTP clients (via mock server), service behavior, and the REST controller.

## Manual Verification Checklist
1. Clean build and tests: `./gradlew clean test`
2. Start service: `./gradlew bootRun`
3. Confirm each symbol has an updated history file under `data/<symbol>.json`
4. Query REST endpoint for configured symbols and verify HTTP 200 + expected data
5. Re-run boot to confirm idempotent behavior (no duplicate API calls when file exists)
