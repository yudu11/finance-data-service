package com.example.financedataservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.financedataservice.client.AlphaVantageClient;
import com.example.financedataservice.client.TwelveDataClient;
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
    private TwelveDataClient twelveDataClient;

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
            twelveDataClient,
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
        when(stockConfig.getGoldDays()).thenReturn(3);

        List<PriceData> goldPrices = List.of(
            new PriceData("XAUUSD", TODAY.minusDays(2), new BigDecimal("2290"), new BigDecimal("2310"),
                new BigDecimal("2280"), new BigDecimal("2300"), null, PriceDataSource.GOLD),
            new PriceData("XAUUSD", TODAY.minusDays(1), new BigDecimal("2305"), new BigDecimal("2320"),
                new BigDecimal("2295"), new BigDecimal("2310"), null, PriceDataSource.GOLD),
            new PriceData("XAUUSD", TODAY, new BigDecimal("2315"), new BigDecimal("2330"),
                new BigDecimal("2305"), new BigDecimal("2325"), null, PriceDataSource.GOLD)
        );
        when(alphaVantageClient.fetchGoldPriceHistory(3)).thenReturn(goldPrices);

        List<PriceData> stockPrices = List.of(
            new PriceData("AAPL", TODAY.minusDays(1), new BigDecimal("180"), new BigDecimal("181"),
                new BigDecimal("179"), new BigDecimal("180.5"), 1000L, PriceDataSource.TWELVE_DATA)
        );
        when(twelveDataClient.fetchHistoricalPrices("AAPL", 30)).thenReturn(stockPrices);

        Path snapshotPath = financeDataService.refreshDailyData();

        assertThat(snapshotPath).exists();
        verify(alphaVantageClient, times(1)).fetchGoldPriceHistory(3);
        verify(twelveDataClient, times(1)).fetchHistoricalPrices("AAPL", 30);

        JsonNode root = objectMapper.readTree(Files.newInputStream(snapshotPath));
        assertThat(root.get("snapshotDate").asText()).isEqualTo("2024-05-16");
        assertThat(root.path("gold").path("symbol").asText()).isEqualTo("XAUUSD");
        assertThat(root.path("goldHistory").isArray()).isTrue();
        assertThat(root.path("goldHistory").size()).isEqualTo(3);
        assertThat(root.path("goldHistory").get(2).path("date").asText()).isEqualTo("2024-05-16");
        assertThat(root.path("stocks").path("AAPL")).isNotNull();
    }

    @Test
    void refreshDailyData_skipsWhenFileExists() throws Exception {
        Path existingFile = tempDir.resolve("2024-05-16").resolve("finance.json");
        Files.createDirectories(existingFile.getParent());
        Files.writeString(existingFile, "{}" );

        Path snapshotPath = financeDataService.refreshDailyData();

        assertThat(snapshotPath).isEqualTo(existingFile);
        verifyNoInteractions(alphaVantageClient, twelveDataClient);
    }

    @Test
    void refreshDailyData_skipsTwelveDataWhenDisabled() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2024-05-16T10:00:00Z"), ZoneOffset.UTC);
        FinanceDataService disabledService = new FinanceDataService(
            alphaVantageClient,
            twelveDataClient,
            stockConfig,
            objectMapper,
            tempDir.toString(),
            fixedClock,
            Duration.ZERO,
            false
        );

        when(stockConfig.getGoldDays()).thenReturn(1);

        List<PriceData> goldPrices = List.of(
            new PriceData("XAUUSD", TODAY, new BigDecimal("2300"), new BigDecimal("2310"),
                new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD)
        );
        when(alphaVantageClient.fetchGoldPriceHistory(1)).thenReturn(goldPrices);

        Path snapshotPath = disabledService.refreshDailyData();

        assertThat(snapshotPath).exists();
        verify(alphaVantageClient, times(1)).fetchGoldPriceHistory(1);
        verifyNoInteractions(twelveDataClient);
        verify(stockConfig, times(1)).getGoldDays();

        JsonNode root = objectMapper.readTree(Files.newInputStream(snapshotPath));
        assertThat(root.path("stocks").isObject()).isTrue();
        assertThat(root.path("stocks").size()).isZero();
    }
}
