package com.example.financedataservice.service;

import com.example.financedataservice.client.AlphaVantageClient;
import com.example.financedataservice.client.FinanceDataClientException;
import com.example.financedataservice.client.YahooFinanceClient;
import com.example.financedataservice.config.StockConfig;
import com.example.financedataservice.model.FinanceSnapshot;
import com.example.financedataservice.model.PriceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FinanceDataService {

    private static final Logger log = LoggerFactory.getLogger(FinanceDataService.class);
    private static final DateTimeFormatter FOLDER_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AlphaVantageClient alphaVantageClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final StockConfig stockConfig;
    private final ObjectMapper objectMapper;
    private final Path baseDirectory;
    private final Clock clock;
    private final Duration yahooRequestDelay;
    private final boolean yahooEnabled;
    private final ReentrantLock refreshLock = new ReentrantLock();

    @Autowired
    public FinanceDataService(AlphaVantageClient alphaVantageClient,
                              YahooFinanceClient yahooFinanceClient,
                              StockConfig stockConfig,
                              ObjectMapper objectMapper,
                              @Value("${finance.data.base-dir:data}") String baseDirectory,
                              @Value("${yahoo-finance.request-delay-ms:500}") long yahooRequestDelayMs,
                              @Value("${yahoo-finance.enabled:true}") boolean yahooEnabled) {
        this(alphaVantageClient, yahooFinanceClient, stockConfig, objectMapper, baseDirectory,
            Clock.systemUTC(), Duration.ofMillis(Math.max(yahooRequestDelayMs, 0)), yahooEnabled);
    }

    FinanceDataService(AlphaVantageClient alphaVantageClient,
                       YahooFinanceClient yahooFinanceClient,
                       StockConfig stockConfig,
                       ObjectMapper objectMapper,
                       String baseDirectory,
                       Clock clock,
                       Duration yahooRequestDelay,
                       boolean yahooEnabled) {
        this.alphaVantageClient = alphaVantageClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.stockConfig = stockConfig;
        this.objectMapper = objectMapper.copy()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baseDirectory = Paths.get(baseDirectory);
        this.clock = clock;
        this.yahooRequestDelay = yahooRequestDelay;
        this.yahooEnabled = yahooEnabled;
    }

    public Path refreshDailyData() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        Path dailyDir = baseDirectory.resolve(FOLDER_FORMATTER.format(today));
        Path snapshotFile = dailyDir.resolve("finance.json");

        if (Files.exists(snapshotFile)) {
            log.info("Daily finance snapshot already exists at {}", snapshotFile);
            return snapshotFile;
        }

        refreshLock.lock();
        try {
            if (Files.exists(snapshotFile)) {
                log.info("Daily finance snapshot already exists after acquiring lock at {}", snapshotFile);
                return snapshotFile;
            }

            Files.createDirectories(dailyDir);
            log.info("Created data directory {}", dailyDir);

            List<PriceData> goldHistory = alphaVantageClient.fetchGoldPriceHistory(stockConfig.getGoldDays());
            Map<String, List<PriceData>> stocks = new HashMap<>();

            if (!yahooEnabled) {
                log.info("Yahoo Finance integration disabled; skipping stock price retrieval");
            } else {
                List<String> symbols = stockConfig.getSymbols();
                if (symbols == null) {
                    symbols = List.of();
                }
                for (int i = 0; i < symbols.size(); i++) {
                    if (i > 0) {
                        sleepBetweenYahooCalls();
                    }
                    String symbol = symbols.get(i);
                    List<PriceData> history = yahooFinanceClient.fetchHistoricalPrices(symbol, stockConfig.getDays());
                    stocks.put(symbol.toUpperCase(), history);
                }
            }

            FinanceSnapshot snapshot = new FinanceSnapshot(today, goldHistory, stocks);
            writeSnapshot(snapshotFile, snapshot);
            log.info("Persisted finance snapshot to {}", snapshotFile);
            return snapshotFile;
        } catch (FinanceDataClientException clientException) {
            throw clientException;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh daily data", e);
        } finally {
            refreshLock.unlock();
        }
    }

    private void sleepBetweenYahooCalls() {
        if (yahooRequestDelay == null || yahooRequestDelay.isZero() || yahooRequestDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(yahooRequestDelay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling Yahoo Finance requests", interruptedException);
        }
    }

    public List<PriceData> getPriceDataForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
        String normalizedSymbol = symbol.toUpperCase();

        if (!Files.exists(baseDirectory) || !Files.isDirectory(baseDirectory)) {
            log.debug("Base directory {} missing or not a directory", baseDirectory);
            return List.of();
        }

        try (Stream<Path> dailyDirs = Files.list(baseDirectory)) {
            List<Path> snapshotFiles = dailyDirs
                .filter(Files::isDirectory)
                .map(dir -> dir.resolve("finance.json"))
                .filter(Files::exists)
                .sorted()
                .toList();

            List<PriceData> aggregated = new ArrayList<>();
            for (Path snapshotFile : snapshotFiles) {
                readSnapshot(snapshotFile).ifPresent(snapshot -> {
                    List<PriceData> goldHistory = snapshot.getGoldHistory();
                    if (!goldHistory.isEmpty()) {
                        goldHistory.stream()
                            .filter(priceData -> normalizedSymbol.equals(priceData.getSymbol()))
                            .forEach(aggregated::add);
                    } else {
                        PriceData gold = snapshot.getGold();
                        if (gold != null && normalizedSymbol.equals(gold.getSymbol())) {
                            aggregated.add(gold);
                        }
                    }
                    List<PriceData> stockData = snapshot.getStocks().get(normalizedSymbol);
                    if (stockData != null) {
                        aggregated.addAll(stockData);
                    }
                });
            }

            aggregated.sort(Comparator.comparing(PriceData::getDate));
            return aggregated;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read price data for symbol " + normalizedSymbol, e);
        }
    }

    private void writeSnapshot(Path snapshotFile, FinanceSnapshot snapshot) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(Files.newOutputStream(snapshotFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), snapshot);
    }

    private java.util.Optional<FinanceSnapshot> readSnapshot(Path snapshotFile) {
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(snapshotFile))) {
            FinanceSnapshot snapshot = objectMapper.readValue(inputStream, FinanceSnapshot.class);
            return java.util.Optional.ofNullable(snapshot);
        } catch (IOException e) {
            log.warn("Failed to deserialize snapshot at {}", snapshotFile, e);
            return java.util.Optional.empty();
        }
    }
}
