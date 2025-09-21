package com.example.financedataservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.financedataservice.model.PriceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestTemplate;

class YahooFinanceClientTest {

    private static final String BASE_URL = "https://yahoo.example";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-05-20T00:00:00Z");

    private MockRestServiceServer mockServer;
    private YahooFinanceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri(BASE_URL).build();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        client = new YahooFinanceClient(restTemplate, new ObjectMapper(), Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
    }

    @Test
    void fetchHistoricalPrices_returnsDailySeries() {
        String body = """
            {
              \"chart\": {
                \"result\": [
                  {
                    \"timestamp\": [1715731200, 1715817600],
                    \"indicators\": {
                      \"quote\": [
                        {
                          \"open\": [190.0, 191.5],
                          \"high\": [192.0, 193.0],
                          \"low\": [189.5, 190.0],
                          \"close\": [191.0, 192.5],
                          \"volume\": [1000000, 1200000]
                        }
                      ]
                    }
                  }
                ],
                \"error\": null
              }
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(
                BASE_URL + "/v8/finance/chart/AAPL?period1=1715731200&period2=1716163200&interval=1d"))
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        List<PriceData> data = client.fetchHistoricalPrices("AAPL", 5);

        mockServer.verify();
        assertThat(data).hasSize(2);
        assertThat(data.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(data.get(0).getDate()).hasToString("2024-05-15");
        assertThat(data.get(1).getClose()).isNotNull();
    }
}
