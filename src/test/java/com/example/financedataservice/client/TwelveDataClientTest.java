package com.example.financedataservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class TwelveDataClientTest {

    private static final String BASE_URL = "https://twelve.example";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-05-20T00:00:00Z");

    private MockRestServiceServer mockServer;
    private TwelveDataClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().rootUri(BASE_URL).build();
        mockServer = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        client = new TwelveDataClient(restTemplate, new ObjectMapper(), Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), "test-key", "1day");
    }

    @Test
    void fetchHistoricalPrices_returnsDailySeries() {
        String body = """
            {
              \"status\": \"ok\",
              \"values\": [
                {
                  \"datetime\": \"2024-05-20\",
                  \"open\": \"190.0\",
                  \"high\": \"192.0\",
                  \"low\": \"189.5\",
                  \"close\": \"191.0\",
                  \"volume\": \"1000000\"
                },
                {
                  \"datetime\": \"2024-05-19 00:00:00\",
                  \"open\": \"191.5\",
                  \"high\": \"193.0\",
                  \"low\": \"190.0\",
                  \"close\": \"192.5\",
                  \"volume\": \"1200000\"
                }
              ]
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(
                BASE_URL + "/time_series?symbol=AAPL&interval=1day&start_date=2024-05-15&end_date=2024-05-20&apikey=test-key"))
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        List<PriceData> data = client.fetchHistoricalPrices("AAPL", 5);

        mockServer.verify();
        assertThat(data).hasSize(2);
        assertThat(data.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(data.get(0).getDate()).hasToString("2024-05-20");
        assertThat(data.get(1).getClose()).isNotNull();
    }

    @Test
    void fetchHistoricalPrices_retriesWhenRateLimited() {
        String uri = BASE_URL + "/time_series?symbol=MSFT&interval=1day&start_date=2024-05-15&end_date=2024-05-20&apikey=test-key";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(org.springframework.http.HttpHeaders.RETRY_AFTER, "1");

        mockServer.expect(MockRestRequestMatchers.requestTo(uri))
            .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body("Too Many Requests"));

        String body = """
            {
              \"status\": \"ok\",
              \"values\": [
                {
                  \"datetime\": \"2024-05-20\",
                  \"open\": \"100.0\",
                  \"high\": \"102.0\",
                  \"low\": \"99.0\",
                  \"close\": \"101.0\",
                  \"volume\": \"50000\"
                }
              ]
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(uri))
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        List<PriceData> data = client.fetchHistoricalPrices("MSFT", 5);

        mockServer.verify();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getSymbol()).isEqualTo("MSFT");
    }

    @Test
    void fetchHistoricalPrices_throwsWhenStatusError() {
        String body = """
            {
              \"status\": \"error\",
              \"message\": \"Rate limit exceeded\"
            }
            """;

        mockServer.expect(MockRestRequestMatchers.requestTo(
                BASE_URL + "/time_series?symbol=TSLA&interval=1day&start_date=2024-05-15&end_date=2024-05-20&apikey=test-key"))
            .andRespond(MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchHistoricalPrices("TSLA", 5))
            .isInstanceOf(FinanceDataClientException.class)
            .hasMessageContaining("Twelve Data reported error");
    }
}
