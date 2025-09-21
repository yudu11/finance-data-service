package com.example.financedataservice.service;

import com.example.financedataservice.client.AlphaVantageClient;
import com.example.financedataservice.client.FinanceDataClientException;
import com.example.financedataservice.client.YahooFinanceClient;
import com.example.financedataservice.config.StockConfig;
import com.example.financedataservice.model.FinanceSnapshot;
import com.example.financedataservice.model.PriceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
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
    private final ReentrantLock refreshLock = new ReentrantLock();

    public FinanceDataService(AlphaVantageClient alphaVantageClient,
                              YahooFinanceClient yahooFinanceClient,
                              StockConfig stockConfig,
                              ObjectMapper objectMapper,
                              @Value("${finance.data.base-dir:data}") String baseDirectory) {
        this(alphaVantageClient, yahooFinanceClient, stockConfig, objectMapper, baseDirectory, Clock.systemUTC());
    }

    FinanceDataService(AlphaVantageClient alphaVantageClient,
                       YahooFinanceClient yahooFinanceClient,
                       StockConfig stockConfig,
                       ObjectMapper objectMapper,
                       String baseDirectory,
                       Clock clock) {
        this.alphaVantageClient = alphaVantageClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.stockConfig = stockConfig;
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.baseDirectory = Paths.get(baseDirectory);
        this.clock = clock;
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

            PriceData gold = alphaVantageClient.fetchLatestGoldPrice();
            Map<String, List<PriceData>> stocks = new HashMap<>();

            for (String symbol : stockConfig.getSymbols()) {
                List<PriceData> history = yahooFinanceClient.fetchHistoricalPrices(symbol, stockConfig.getDays());
                stocks.put(symbol.toUpperCase(), history);
            }

            FinanceSnapshot snapshot = new FinanceSnapshot(today, gold, stocks);
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
                .collect(Collectors.toList());

            List<PriceData> aggregated = new ArrayList<>();
            for (Path snapshotFile : snapshotFiles) {
                readSnapshot(snapshotFile).ifPresent(snapshot -> {
                    PriceData gold = snapshot.getGold();
                    if (gold != null && normalizedSymbol.equals(gold.getSymbol())) {
                        aggregated.add(gold);
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
