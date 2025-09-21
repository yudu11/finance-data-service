package com.example.financedataservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class AlphaVantageClientTest {

    private static final String BASE_URL = "https://alphavantage.example";

    private MockRestServiceServer mockServer;
    private AlphaVantageClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri(BASE_URL).build();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        client = new AlphaVantageClient(restTemplate, new ObjectMapper(), "test");
    }

    @Test
    void fetchLatestGoldPrice_returnsLatestEntry() {
        String body = """
            {
              \"Meta Data\": {},
              \"Time Series (Daily)\": {
                \"2024-05-15\": {
                  \"1. open\": \"2320.0000\",
                  \"2. high\": \"2330.0000\",
                  \"3. low\": \"2310.0000\",
                  \"4. close\": \"2325.0000\"
                },
                \"2024-05-16\": {
                  \"1. open\": \"2330.0000\",
                  \"2. high\": \"2340.0000\",
                  \"3. low\": \"2320.0000\",
                  \"4. close\": \"2335.5000\"
                }
              }
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(
                BASE_URL + "/query?function=TIME_SERIES_DAILY&symbol=XAUUSD&outputsize=full&datatype=json&apikey=test"))
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        PriceData priceData = client.fetchLatestGoldPrice();

        mockServer.verify();
        assertThat(priceData.getSymbol()).isEqualTo("XAUUSD");
        assertThat(priceData.getDate()).hasToString("2024-05-16");
        assertThat(priceData.getClose()).isNotNull();
        assertThat(priceData.getSource()).isEqualTo(PriceDataSource.GOLD);
    }
}
