package com.example.financedataservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.financedataservice.model.PriceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

class YahooFinanceClientTest {

    private static final String BASE_URL = "https://yahoo.example";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-05-20T00:00:00Z");
    private static final String EXPECTED_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7";
    private static final String EXPECTED_ACCEPT_ENCODING = "gzip, deflate, br, zstd";
    private static final String EXPECTED_ACCEPT_LANGUAGE = "en,zh-CN;q=0.9,zh;q=0.8,zh-TW;q=0.7";
    private static final String EXPECTED_COOKIE = "tbla_id=066f866f-1a4d-4e2c-bf77-11ea2d8090fe-tuctbb6c769; axids=gam=y-X_K_bOJE2uIGfhJNFTftk777.Lnbk9bh~A&dv360=eS1DMTI4WDQxRTJ1SDNlSDlRSlY0ZURadDZGZXdKbFpqeH5B&ydsp=y-4fR7SSlE2uKm5s2u1ZseDcv4hucdrcbK~A&tbla=y-_FXT6T1E2uI.H987jwEnCL0KYHvTpNxV~A; cmp=t=1738535966&j=0&u=1---; A3=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE; A1=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE; A1S=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE";

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

    @Test
    void fetchHistoricalPrices_retriesWhenRateLimited() {
        String uri = BASE_URL + "/v8/finance/chart/MSFT?period1=1715731200&period2=1716163200&interval=1d";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add(org.springframework.http.HttpHeaders.RETRY_AFTER, "1");

        mockServer.expect(MockRestRequestMatchers.requestTo(uri))
            .andRespond(MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body("Edge: Too Many Requests"));

        String body = """
            {
              \"chart\": {
                \"result\": [
                  {
                    \"timestamp\": [1715731200],
                    \"indicators\": {
                      \"quote\": [
                        {
                          \"open\": [100.0],
                          \"high\": [102.0],
                          \"low\": [99.0],
                          \"close\": [101.0],
                          \"volume\": [50000]
                        }
                      ]
                    }
                  }
                ],
                \"error\": null
              }
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
    void defaultHeaders_includeBrowserLikeValues() {
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) ReflectionTestUtils.getField(YahooFinanceClient.class, "DEFAULT_HEADERS");

        assertThat(headers).isNotNull();
        assertThat(headers)
            .containsEntry(org.springframework.http.HttpHeaders.USER_AGENT, YahooFinanceClient.DEFAULT_USER_AGENT)
            .containsEntry(org.springframework.http.HttpHeaders.ACCEPT, EXPECTED_ACCEPT)
            .containsEntry(org.springframework.http.HttpHeaders.ACCEPT_ENCODING, EXPECTED_ACCEPT_ENCODING)
            .containsEntry(org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE, EXPECTED_ACCEPT_LANGUAGE)
            .containsEntry(org.springframework.http.HttpHeaders.COOKIE, EXPECTED_COOKIE)
            .containsEntry("priority", "u=0, i")
            .containsEntry("sec-ch-ua", "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\"")
            .containsEntry("sec-ch-ua-mobile", "?0")
            .containsEntry("sec-ch-ua-platform", "\"macOS\"")
            .containsEntry("sec-fetch-dest", "document")
            .containsEntry("sec-fetch-mode", "navigate")
            .containsEntry("sec-fetch-site", "none")
            .containsEntry("sec-fetch-user", "?1")
            .containsEntry("upgrade-insecure-requests", "1")
            .containsEntry("DNT", "1");
    }
}
