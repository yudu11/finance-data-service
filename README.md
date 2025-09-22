# FinanceDataService

FinanceDataService is a Spring Boot application that collects gold and stock price data on startup, stores it locally as JSON snapshots, and exposes a REST endpoint to query historical prices.

## Project Structure
- `build.gradle.kts` – Gradle build configuration using Spring Boot 3 and Java 17.
- `src/main/java/com/example/financedataservice` – Application code organized into configuration, client, service, controller, model, and bootstrap packages.
- `src/main/resources/config/stocks.json` – Default stock symbols and lookback days for Yahoo Finance integration.
- `data/` – Generated daily snapshot files (`YYYY-MM-DD/finance.json`).

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

### Manual Docker Commands
```bash
# Build local image
docker build -t finance-data-service:latest .

# Push to registry
docker tag finance-data-service:latest docker.io/username/finance-data-service:latest
docker push docker.io/username/finance-data-service:latest

# Run locally
docker run -d -p 8080:8080 --restart unless-stopped --name finance-data-service finance-data-service:latest
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
3. Confirm snapshot creation under `data/<today>/finance.json`
4. Query REST endpoint for configured symbols and verify HTTP 200 + expected data
5. Re-run boot to confirm idempotent behavior (no duplicate API calls when file exists)
