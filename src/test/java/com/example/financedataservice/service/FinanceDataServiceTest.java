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
import com.example.financedataservice.model.SymbolPriceHistory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        financeDataService = new FinanceDataService(
            alphaVantageClient,
            twelveDataClient,
            stockConfig,
            objectMapper,
            tempDir.toString(),
            Duration.ZERO,
            true
        );
    }

    @Test
    void refreshDailyData_persistsPerSymbolFiles() throws Exception {
        when(stockConfig.getSymbols()).thenReturn(List.of("AAPL"));
        when(stockConfig.getDays()).thenReturn(30);
        when(stockConfig.getGoldDays()).thenReturn(3);

        List<PriceData> goldPrices = List.of(
            new PriceData("XAUUSD", TODAY.minusDays(1), new BigDecimal("2300"), new BigDecimal("2310"),
                new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD),
            new PriceData("XAUUSD", TODAY, new BigDecimal("2315"), new BigDecimal("2330"),
                new BigDecimal("2305"), new BigDecimal("2325"), null, PriceDataSource.GOLD)
        );
        when(alphaVantageClient.fetchGoldPriceHistory(3)).thenReturn(goldPrices);

        List<PriceData> stockPrices = List.of(
            new PriceData("AAPL", TODAY.minusDays(1), new BigDecimal("180"), new BigDecimal("181"),
                new BigDecimal("179"), new BigDecimal("180.5"), 1000L, PriceDataSource.TWELVE_DATA),
            new PriceData("AAPL", TODAY, new BigDecimal("181"), new BigDecimal("182"),
                new BigDecimal("180"), new BigDecimal("181.5"), 1200L, PriceDataSource.TWELVE_DATA)
        );
        when(twelveDataClient.fetchHistoricalPrices("AAPL", 30)).thenReturn(stockPrices);

        Path resultPath = financeDataService.refreshDailyData();

        assertThat(resultPath).isEqualTo(tempDir);
        Path goldFile = tempDir.resolve("XAUUSD.json");
        Path stockFile = tempDir.resolve("AAPL.json");
        assertThat(goldFile).exists();
        assertThat(stockFile).exists();

        SymbolPriceHistory goldHistory = objectMapper.readValue(goldFile.toFile(), SymbolPriceHistory.class);
        assertThat(goldHistory.getSymbol()).isEqualTo("XAUUSD");
        assertThat(goldHistory.getPrices()).hasSize(2);
        assertThat(goldHistory.getPrices().get(1).getClose()).isEqualTo(new BigDecimal("2325"));

        SymbolPriceHistory stockHistory = objectMapper.readValue(stockFile.toFile(), SymbolPriceHistory.class);
        assertThat(stockHistory.getSymbol()).isEqualTo("AAPL");
        assertThat(stockHistory.getPrices()).hasSize(2);
        assertThat(stockHistory.getPrices().get(0).getOpen()).isEqualTo(new BigDecimal("180"));

        verify(alphaVantageClient, times(1)).fetchGoldPriceHistory(3);
        verify(twelveDataClient, times(1)).fetchHistoricalPrices("AAPL", 30);
    }

    @Test
    void refreshDailyData_appendsOnlyNewEntries() throws Exception {
        when(stockConfig.getSymbols()).thenReturn(List.of("AAPL"));
        when(stockConfig.getDays()).thenReturn(30);
        when(stockConfig.getGoldDays()).thenReturn(2);

        PriceData existing = new PriceData("AAPL", TODAY.minusDays(1), new BigDecimal("180"), new BigDecimal("181"),
            new BigDecimal("179"), new BigDecimal("180.5"), 1000L, PriceDataSource.TWELVE_DATA);
        SymbolPriceHistory existingHistory = new SymbolPriceHistory("AAPL", List.of(existing));
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(tempDir.resolve("AAPL.json").toFile(), existingHistory);

        List<PriceData> fresh = List.of(
            existing,
            new PriceData("AAPL", TODAY, new BigDecimal("181"), new BigDecimal("183"),
                new BigDecimal("180.5"), new BigDecimal("182.5"), 1100L, PriceDataSource.TWELVE_DATA)
        );
        when(alphaVantageClient.fetchGoldPriceHistory(2)).thenReturn(List.of(
            new PriceData("XAUUSD", TODAY.minusDays(1), new BigDecimal("2300"), new BigDecimal("2310"),
                new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD)
        ));
        when(twelveDataClient.fetchHistoricalPrices("AAPL", 30)).thenReturn(fresh);

        financeDataService.refreshDailyData();

        SymbolPriceHistory updatedHistory = objectMapper.readValue(tempDir.resolve("AAPL.json").toFile(), SymbolPriceHistory.class);
        assertThat(updatedHistory.getPrices()).hasSize(2);
        assertThat(updatedHistory.getPrices().get(1).getDate()).isEqualTo(TODAY);
    }

    @Test
    void refreshDailyData_skipsTwelveDataWhenDisabled() throws Exception {
        FinanceDataService disabledService = new FinanceDataService(
            alphaVantageClient,
            twelveDataClient,
            stockConfig,
            objectMapper,
            tempDir.toString(),
            Duration.ZERO,
            false
        );

        when(stockConfig.getGoldDays()).thenReturn(1);
        when(alphaVantageClient.fetchGoldPriceHistory(1)).thenReturn(List.of(
            new PriceData("XAUUSD", TODAY, new BigDecimal("2300"), new BigDecimal("2310"),
                new BigDecimal("2290"), new BigDecimal("2305"), null, PriceDataSource.GOLD)
        ));

        disabledService.refreshDailyData();

        verify(alphaVantageClient, times(1)).fetchGoldPriceHistory(1);
        verifyNoInteractions(twelveDataClient);
        assertThat(Files.exists(tempDir.resolve("XAUUSD.json"))).isTrue();
    }

    @Test
    void getPriceDataForSymbol_readsFromDiskWhenCacheEmpty() throws Exception {
        PriceData first = new PriceData("AAPL", TODAY.minusDays(1), new BigDecimal("180"), new BigDecimal("181"),
            new BigDecimal("179"), new BigDecimal("180.5"), 1000L, PriceDataSource.TWELVE_DATA);
        PriceData second = new PriceData("AAPL", TODAY, new BigDecimal("181"), new BigDecimal("182"),
            new BigDecimal("180"), new BigDecimal("181.5"), 1200L, PriceDataSource.TWELVE_DATA);
        SymbolPriceHistory cached = new SymbolPriceHistory("AAPL", List.of(first, second));
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(tempDir.resolve("AAPL.json").toFile(), cached);

        List<PriceData> result = financeDataService.getPriceDataForSymbol("aapl");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(result.get(1).getDate()).isEqualTo(TODAY);
    }
}
