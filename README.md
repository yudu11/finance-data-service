# FinanceDataService

FinanceDataService is a Spring Boot application that collects gold and stock price data on startup, stores it locally as JSON snapshots, and exposes a REST endpoint to query historical prices.

## Project Structure
- `build.gradle.kts` – Gradle build configuration using Spring Boot 3 and Java 17.
- `src/main/java/com/example/financedataservice` – Application code organized into configuration, client, service, controller, model, and bootstrap packages.
- `src/main/resources/config/stocks.json` – Default stock symbols and lookback days for Twelve Data integration.
- `data/` – Per-symbol JSON histories persisted on disk (`{SYMBOL}.json`).

## Running Locally
1. Ensure JDK 17+ and Gradle or the Gradle Wrapper (`./gradlew`) are available.
2. (Optional) Update `src/main/resources/config/stocks.json` with desired symbols.
3. Run the application:
   ```bash
   ./gradlew bootRun
   ```
   On startup the app downloads gold and stock data for the current day (if not already stored).

## Docker Usage
### Prerequisites
- Docker Desktop (or Docker Engine) is running locally.
- (Optional) Logged in to your Docker registry (`docker login`).

### Build, Push & Run via Script
Use the helper script to build the JAR, create the Docker image, optionally push it, and start the local container:
```bash
./docker_build_run.sh
```
Environment variables:
- `IMAGE_TAG` (default `finance-data-service:latest`)
- `REMOTE_IMAGE` (default `docker.io/username/finance-data-service:latest`)
- `CONTAINER_NAME` (default `finance-data-service`)
- `ENV_FILE` path to load environment variables into the container
- `PUSH_IMAGE` (`true` by default). Set to `false` to skip pushing while still running the local image.
- `DATA_DIR` host folder to bind mount at `/app/data` (default `${PWD}/data`).

Example without pushing to a registry:
```bash
PUSH_IMAGE=false ./docker_build_run.sh
```

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
Spin up a Kubernetes in Docker cluster with one server and two agents:
```bash
k3d cluster create finance --agents 2 --api-port 6443
```

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
The NodePort service publishes port 8080 inside the cluster. To reach it from your workstation, forward the port through kubectl and keep the tunnel open while testing:
```bash
kubectl port-forward svc/finance-finance-data-service 8080:8080 -n finance
# In a second terminal
curl "http://localhost:8080/getPriceData?symbol=AAPL"
```

You can also verify networking from within the k3d Docker network without port forwarding:
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
