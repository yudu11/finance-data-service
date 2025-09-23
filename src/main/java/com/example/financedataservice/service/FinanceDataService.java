package com.example.financedataservice.service;

import com.example.financedataservice.client.AlphaVantageClient;
import com.example.financedataservice.client.FinanceDataClientException;
import com.example.financedataservice.client.TwelveDataClient;
import com.example.financedataservice.config.StockConfig;
import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.example.financedataservice.model.SymbolPriceHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FinanceDataService {

    private static final Logger log = LoggerFactory.getLogger(FinanceDataService.class);
    private static final String GOLD_SYMBOL = "XAUUSD";

    private final AlphaVantageClient alphaVantageClient;
    private final TwelveDataClient twelveDataClient;
    private final StockConfig stockConfig;
    private final ObjectMapper objectMapper;
    private final Path baseDirectory;
    private final Duration twelveDataRequestDelay;
    private final boolean twelveDataEnabled;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final Map<String, NavigableMap<LocalDate, PriceData>> priceCache = new ConcurrentHashMap<>();

    @Autowired
    public FinanceDataService(AlphaVantageClient alphaVantageClient,
                              TwelveDataClient twelveDataClient,
                              StockConfig stockConfig,
                              ObjectMapper objectMapper,
                              @Value("${finance.data.base-dir:data}") String baseDirectory,
                              @Value("${twelve-data.request-delay-ms:500}") long twelveDataRequestDelayMs,
                              @Value("${twelve-data.enabled:true}") boolean twelveDataEnabled) {
        this(alphaVantageClient, twelveDataClient, stockConfig, objectMapper, baseDirectory,
            Duration.ofMillis(Math.max(twelveDataRequestDelayMs, 0)), twelveDataEnabled);
    }

    FinanceDataService(AlphaVantageClient alphaVantageClient,
                       TwelveDataClient twelveDataClient,
                       StockConfig stockConfig,
                       ObjectMapper objectMapper,
                       String baseDirectory,
                       Duration twelveDataRequestDelay,
                       boolean twelveDataEnabled) {
        this.alphaVantageClient = alphaVantageClient;
        this.twelveDataClient = twelveDataClient;
        this.stockConfig = stockConfig;
        this.objectMapper = objectMapper.copy()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.baseDirectory = Paths.get(baseDirectory);
        this.twelveDataRequestDelay = twelveDataRequestDelay;
        this.twelveDataEnabled = twelveDataEnabled;
    }

    public Path refreshDailyData() {
        refreshLock.lock();
        try {
            ensureBaseDirectory();

            int newDataPoints = 0;

            int goldDays = stockConfig.getGoldDays();
            if (goldDays > 0) {
                List<PriceData> goldHistory = alphaVantageClient.fetchGoldPriceHistory(goldDays);
                newDataPoints += mergeAndPersist(GOLD_SYMBOL, goldHistory);
            } else {
                log.info("Gold lookback configured to {} days; skipping AlphaVantage fetch", goldDays);
            }

            if (!twelveDataEnabled) {
                log.info("Twelve Data integration disabled; skipping stock price retrieval");
            } else {
                List<String> symbols = Optional.ofNullable(stockConfig.getSymbols()).orElse(Collections.emptyList());
                int days = stockConfig.getDays();
                for (int i = 0; i < symbols.size(); i++) {
                    if (i > 0) {
                        sleepBetweenTwelveDataCalls();
                    }
                    String symbol = symbols.get(i);
                    if (!stringHasText(symbol)) {
                        continue;
                    }
                    if (days <= 0) {
                        log.warn("Days configuration is {}. Skipping Twelve Data fetch for {}", days, symbol);
                        continue;
                    }
                    List<PriceData> history = twelveDataClient.fetchHistoricalPrices(symbol, days);
                    newDataPoints += mergeAndPersist(symbol, history);
                }
            }

            log.info("Completed data refresh. {} new data points persisted.", newDataPoints);
            return baseDirectory;
        } catch (FinanceDataClientException clientException) {
            throw clientException;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to refresh price data", e);
        } finally {
            refreshLock.unlock();
        }
    }

    public List<PriceData> getPriceDataForSymbol(String symbol) {
        if (!stringHasText(symbol)) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
        String normalizedSymbol = symbol.toUpperCase();

        NavigableMap<LocalDate, PriceData> cache = loadCache(normalizedSymbol);
        if (cache.isEmpty()) {
            log.debug("No cached data available for symbol {}", normalizedSymbol);
            return List.of();
        }

        return new ArrayList<>(cache.values());
    }

    private int mergeAndPersist(String symbol, List<PriceData> freshData) throws IOException {
        if (freshData == null || freshData.isEmpty()) {
            log.debug("No data returned for symbol {}", symbol);
            return 0;
        }

        String normalizedSymbol = symbol.toUpperCase();
        NavigableMap<LocalDate, PriceData> cache = loadCache(normalizedSymbol);

        int newEntries = 0;
        for (PriceData price : freshData) {
            PriceData normalized = normalizePriceData(normalizedSymbol, price);
            if (normalized == null) {
                continue;
            }
            if (normalized.getDate() == null) {
                log.warn("Dropping data point with missing date for symbol {}", normalizedSymbol);
                continue;
            }
            PriceData existing = cache.get(normalized.getDate());
            if (existing == null || !priceEquals(existing, normalized)) {
                cache.put(normalized.getDate(), normalized);
                newEntries++;
            }
        }

        if (newEntries > 0) {
            persistSymbolData(normalizedSymbol, cache);
        }

        return newEntries;
    }

    private PriceData normalizePriceData(String normalizedSymbol, PriceData price) {
        if (price == null) {
            return null;
        }
        return new PriceData(
            normalizedSymbol,
            price.getDate(),
            price.getOpen(),
            price.getHigh(),
            price.getLow(),
            price.getClose(),
            price.getVolume(),
            price.getSource() == null ? resolveSource(normalizedSymbol) : price.getSource()
        );
    }

    private PriceDataSource resolveSource(String normalizedSymbol) {
        return GOLD_SYMBOL.equalsIgnoreCase(normalizedSymbol) ? PriceDataSource.GOLD : PriceDataSource.TWELVE_DATA;
    }

    private NavigableMap<LocalDate, PriceData> loadCache(String symbol) {
        return priceCache.computeIfAbsent(symbol, this::loadFromDisk);
    }

    private NavigableMap<LocalDate, PriceData> loadFromDisk(String symbol) {
        NavigableMap<LocalDate, PriceData> map = new ConcurrentSkipListMap<>();
        Path file = resolveSymbolFile(symbol);
        if (!Files.exists(file)) {
            return map;
        }
        try {
            SymbolPriceHistory history = objectMapper.readValue(file.toFile(), SymbolPriceHistory.class);
            if (history.getPrices() == null) {
                return map;
            }
            history.getPrices().stream()
                .map(price -> normalizePriceData(symbol, price))
                .filter(Objects::nonNull)
                .filter(price -> price.getDate() != null)
                .forEach(price -> map.put(price.getDate(), price));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load cached data for symbol " + symbol, e);
        }
        return map;
    }

    private void persistSymbolData(String symbol, NavigableMap<LocalDate, PriceData> cache) throws IOException {
        ensureBaseDirectory();
        Path file = resolveSymbolFile(symbol);
        Files.createDirectories(file.getParent());

        SymbolPriceHistory history = new SymbolPriceHistory(symbol, new ArrayList<>(cache.values()));
        Path tempFile = Files.createTempFile(baseDirectory, symbol + "-", ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), history);
        try {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException atomicMoveNotSupportedException) {
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureBaseDirectory() throws IOException {
        Files.createDirectories(baseDirectory);
    }

    private Path resolveSymbolFile(String symbol) {
        return baseDirectory.resolve(symbol + ".json");
    }

    private boolean priceEquals(PriceData first, PriceData second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return Objects.equals(first.getSymbol(), second.getSymbol())
            && Objects.equals(first.getDate(), second.getDate())
            && Objects.equals(first.getOpen(), second.getOpen())
            && Objects.equals(first.getHigh(), second.getHigh())
            && Objects.equals(first.getLow(), second.getLow())
            && Objects.equals(first.getClose(), second.getClose())
            && Objects.equals(first.getVolume(), second.getVolume())
            && Objects.equals(first.getSource(), second.getSource());
    }

    private void sleepBetweenTwelveDataCalls() {
        if (twelveDataRequestDelay == null || twelveDataRequestDelay.isZero() || twelveDataRequestDelay.isNegative()) {
            return;
        }
        try {
            Thread.sleep(twelveDataRequestDelay.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling Twelve Data requests", interruptedException);
        }
    }

    private boolean stringHasText(String value) {
        return value != null && !value.isBlank();
    }
}
