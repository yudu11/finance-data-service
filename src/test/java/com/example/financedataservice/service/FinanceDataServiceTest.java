package com.example.financedataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.financedataservice.client.AlphaVantageClient;
import com.example.financedataservice.client.YahooFinanceClient;
import com.example.financedataservice.config.StockConfig;
import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class FinanceDataServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2024, 5, 16);

    @Mock
    private AlphaVantageClient alphaVantageClient;

    @Mock
    private YahooFinanceClient yahooFinanceClient;

    @Mock
    private StockConfig stockConfig;

    private FinanceDataService financeDataService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-05-16T10:00:00Z"), ZoneOffset.UTC);
        financeDataService = new FinanceDataService(
            alphaVantageClient,
            yahooFinanceClient,
            stockConfig,
            objectMapper,
            tempDir.toString(),
            fixedClock,
            Duration.ZERO,
            true
        );
    }

    @Test
    void refreshDailyData_createsSnapshotWhenMissing() throws Exception {
        when(stockConfig.getSymbols()).thenReturn(List.of("AAPL"));
        when(stockConfig.getDays()).thenReturn(30);

        PriceData goldPrice = new PriceData("XAUUSD", TODAY, new BigDecimal("2300"), new BigDecimal("2310"),
            new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD);
        when(alphaVantageClient.fetchLatestGoldPrice()).thenReturn(goldPrice);

        List<PriceData> stockPrices = List.of(
            new PriceData("AAPL", TODAY.minusDays(1), new BigDecimal("180"), new BigDecimal("181"),
                new BigDecimal("179"), new BigDecimal("180.5"), 1000L, PriceDataSource.YAHOO_FINANCE)
        );
        when(yahooFinanceClient.fetchHistoricalPrices("AAPL", 30)).thenReturn(stockPrices);

        Path snapshotPath = financeDataService.refreshDailyData();

        assertThat(snapshotPath).exists();
        verify(alphaVantageClient, times(1)).fetchLatestGoldPrice();
        verify(yahooFinanceClient, times(1)).fetchHistoricalPrices("AAPL", 30);

        JsonNode root = objectMapper.readTree(Files.newInputStream(snapshotPath));
        assertThat(root.get("snapshotDate").asText()).isEqualTo("2024-05-16");
        assertThat(root.path("gold").path("symbol").asText()).isEqualTo("XAUUSD");
        assertThat(root.path("stocks").path("AAPL")).isNotNull();
    }

    @Test
    void refreshDailyData_skipsWhenFileExists() throws Exception {
        Path existingFile = tempDir.resolve("2024-05-16").resolve("finance.json");
        Files.createDirectories(existingFile.getParent());
        Files.writeString(existingFile, "{}" );

        Path snapshotPath = financeDataService.refreshDailyData();

        assertThat(snapshotPath).isEqualTo(existingFile);
        verifyNoInteractions(alphaVantageClient, yahooFinanceClient);
    }

    @Test
    void refreshDailyData_skipsYahooWhenDisabled() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-05-16T10:00:00Z"), ZoneOffset.UTC);
        FinanceDataService disabledService = new FinanceDataService(
            alphaVantageClient,
            yahooFinanceClient,
            stockConfig,
            objectMapper,
            tempDir.toString(),
            fixedClock,
            Duration.ZERO,
            false
        );

        PriceData goldPrice = new PriceData("XAUUSD", TODAY, new BigDecimal("2300"), new BigDecimal("2310"),
            new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD);
        when(alphaVantageClient.fetchLatestGoldPrice()).thenReturn(goldPrice);

        Path snapshotPath = disabledService.refreshDailyData();

        assertThat(snapshotPath).exists();
        verify(alphaVantageClient, times(1)).fetchLatestGoldPrice();
        verifyNoInteractions(yahooFinanceClient, stockConfig);

        JsonNode root = objectMapper.readTree(Files.newInputStream(snapshotPath));
        assertThat(root.path("stocks").isObject()).isTrue();
        assertThat(root.path("stocks").size()).isZero();
    }
}
