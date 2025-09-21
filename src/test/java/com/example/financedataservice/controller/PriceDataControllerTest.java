package com.example.financedataservice.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.example.financedataservice.service.FinanceDataService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PriceDataControllerTest {

    private FinanceDataService financeDataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        financeDataService = Mockito.mock(FinanceDataService.class);
        PriceDataController controller = new PriceDataController(financeDataService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getPriceData_returnsData() throws Exception {
        PriceData priceData = new PriceData(
            "AAPL",
            LocalDate.of(2024, 5, 16),
            new BigDecimal("180.00"),
            new BigDecimal("181.00"),
            new BigDecimal("179.00"),
            new BigDecimal("180.50"),
            1000L,
            PriceDataSource.YAHOO_FINANCE
        );

        when(financeDataService.getPriceDataForSymbol("AAPL"))
            .thenReturn(List.of(priceData));

        mockMvc.perform(get("/getPriceData").queryParam("symbol", "AAPL")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].symbol").value("AAPL"));
    }

    @Test
    void getPriceData_returns404WhenEmpty() throws Exception {
        when(financeDataService.getPriceDataForSymbol("AAPL")).thenReturn(List.of());

        mockMvc.perform(get("/getPriceData").queryParam("symbol", "AAPL"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getPriceData_returns400WhenMissingSymbol() throws Exception {
        mockMvc.perform(get("/getPriceData"))
            .andExpect(status().isBadRequest());
    }
}
