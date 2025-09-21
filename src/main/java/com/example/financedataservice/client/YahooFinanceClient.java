package com.example.financedataservice.client;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);
    private static final int MAX_RETRIES = 3;
    static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";
    private static final Map<String, String> DEFAULT_HEADERS = Map.ofEntries(
        Map.entry(HttpHeaders.USER_AGENT, DEFAULT_USER_AGENT),
        Map.entry(HttpHeaders.ACCEPT,
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"),
        Map.entry(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br, zstd"),
        Map.entry(HttpHeaders.ACCEPT_LANGUAGE, "en,zh-CN;q=0.9,zh;q=0.8,zh-TW;q=0.7"),
        Map.entry(HttpHeaders.COOKIE,
            "tbla_id=066f866f-1a4d-4e2c-bf77-11ea2d8090fe-tuctbb6c769; axids=gam=y-X_K_bOJE2uIGfhJNFTftk777.Lnbk9bh~A&dv360=eS1DMTI4WDQxRTJ1SDNlSDlRSlY0ZURadDZGZXdKbFpqeH5B&ydsp=y-4fR7SSlE2uKm5s2u1ZseDcv4hucdrcbK~A&tbla=y-_FXT6T1E2uI.H987jwEnCL0KYHvTpNxV~A; cmp=t=1738535966&j=0&u=1---; A3=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE; A1=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE; A1S=d=AQABBOpBvWQCEIg-iWAtPBUdJNbj9UnvU6MFEgEBCAGKcmeiZ6-0b2UB_eMDAAcI6kG9ZEnvU6M&S=AQAAAkePYf3ecm_lhHlByl2IsEE"),
        Map.entry("DNT", "1"),
        Map.entry("priority", "u=0, i"),
        Map.entry("sec-ch-ua", "\"Not;A=Brand\";v=\"99\", \"Google Chrome\";v=\"139\", \"Chromium\";v=\"139\""),
        Map.entry("sec-ch-ua-mobile", "?0"),
        Map.entry("sec-ch-ua-platform", "\"macOS\""),
        Map.entry("sec-fetch-dest", "document"),
        Map.entry("sec-fetch-mode", "navigate"),
        Map.entry("sec-fetch-site", "none"),
        Map.entry("sec-fetch-user", "?1"),
        Map.entry("upgrade-insecure-requests", "1")
    );

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public YahooFinanceClient(RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper,
                              @Value("${yahoo-finance.base-url}") String baseUrl) {
        this(buildRestTemplate(restTemplateBuilder, baseUrl), objectMapper, Clock.systemUTC());
    }

    YahooFinanceClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this(restTemplate, objectMapper, Clock.systemUTC());
    }

    private static RestTemplate buildRestTemplate(RestTemplateBuilder restTemplateBuilder, String baseUrl) {
        RestTemplateBuilder builder = restTemplateBuilder.rootUri(baseUrl);
        for (Map.Entry<String, String> header : DEFAULT_HEADERS.entrySet()) {
            builder = builder.defaultHeader(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    YahooFinanceClient(RestTemplate restTemplate, ObjectMapper objectMapper, Clock clock) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public List<PriceData> fetchHistoricalPrices(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Days parameter must be greater than zero");
        }

        Instant endInstant = Instant.now(clock);
        Instant startInstant = endInstant.minus(Duration.ofDays(days));

        String uri = UriComponentsBuilder.fromPath(String.format("/v8/finance/chart/%s", symbol))
            .queryParam("period1", startInstant.getEpochSecond())
            .queryParam("period2", endInstant.getEpochSecond())
            .queryParam("interval", "1d")
            .build(true)
            .toUriString();

        try {
            String body = fetchWithRetry(symbol, uri);
            JsonNode root = objectMapper.readTree(body);
            JsonNode chart = root.path("chart");
            if (chart.isMissingNode()) {
                throw new FinanceDataClientException("Yahoo Finance response missing chart node");
            }
            JsonNode errorNode = chart.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                throw new FinanceDataClientException("Yahoo Finance reported error: " + errorNode.toString());
            }

            JsonNode resultsNode = chart.path("result");
            if (!resultsNode.isArray() || resultsNode.isEmpty()) {
                throw new FinanceDataClientException("Yahoo Finance response missing result data");
            }

            JsonNode resultNode = resultsNode.get(0);
            JsonNode timestamps = resultNode.path("timestamp");
            JsonNode indicators = resultNode.path("indicators").path("quote");
            if (!timestamps.isArray() || !indicators.isArray() || indicators.isEmpty()) {
                throw new FinanceDataClientException("Yahoo Finance response incomplete for symbol " + symbol);
            }

            JsonNode quotesNode = indicators.get(0);
            List<PriceData> dataPoints = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode tsNode = timestamps.get(i);
                if (tsNode == null || tsNode.isNull()) {
                    continue;
                }
                long epochSeconds = tsNode.asLong();
                LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();

                BigDecimal open = readDecimal(quotesNode, "open", i);
                BigDecimal high = readDecimal(quotesNode, "high", i);
                BigDecimal low = readDecimal(quotesNode, "low", i);
                BigDecimal close = readDecimal(quotesNode, "close", i);
                Long volume = readLong(quotesNode, "volume", i);

                dataPoints.add(new PriceData(symbol.toUpperCase(), date, open, high, low, close, volume, PriceDataSource.YAHOO_FINANCE));
            }

            return dataPoints;
        } catch (IOException e) {
            log.error("Failed to parse Yahoo Finance response for symbol {}", symbol, e);
            throw new FinanceDataClientException("Failed to parse Yahoo Finance response", e);
        }
    }

    private String fetchWithRetry(String symbol, String uri) {
        int attempts = 0;
        while (true) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new FinanceDataClientException("Failed to fetch stock prices for " + symbol);
                }
                return response.getBody();
            } catch (HttpClientErrorException.TooManyRequests tooManyRequests) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    throw new FinanceDataClientException("Yahoo Finance rate limit exceeded for " + symbol, tooManyRequests);
                }
                Duration delay = resolveRetryDelay(tooManyRequests.getResponseHeaders());
                long sleepMillis = Math.max(delay.toMillis(), DEFAULT_RETRY_DELAY.toMillis());
                log.warn("Yahoo Finance rate limited for {}. Retrying in {} ms (attempt {}/{})",
                    symbol, sleepMillis, attempts, MAX_RETRIES);
                sleep(sleepMillis);
            }
        }
    }

    private Duration resolveRetryDelay(HttpHeaders headers) {
        if (headers == null) {
            return DEFAULT_RETRY_DELAY;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_DELAY;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException numberFormatException) {
            try {
                ZonedDateTime retryDate = ZonedDateTime.parse(retryAfter.trim());
                Duration duration = Duration.between(Instant.now(clock), retryDate.toInstant());
                return duration.isNegative() ? DEFAULT_RETRY_DELAY : duration;
            } catch (DateTimeParseException ignored) {
                return DEFAULT_RETRY_DELAY;
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new FinanceDataClientException("Interrupted while waiting to retry Yahoo Finance request", interruptedException);
        }
    }

    private BigDecimal readDecimal(JsonNode quotesNode, String fieldName, int index) {
        JsonNode arrayNode = quotesNode.path(fieldName);
        if (!arrayNode.isArray() || index >= arrayNode.size()) {
            throw new FinanceDataClientException("Yahoo Finance response missing field " + fieldName);
        }
        JsonNode valueNode = arrayNode.get(index);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return new BigDecimal(valueNode.asText());
    }

    private Long readLong(JsonNode quotesNode, String fieldName, int index) {
        JsonNode arrayNode = quotesNode.path(fieldName);
        if (!arrayNode.isArray() || index >= arrayNode.size()) {
            return null;
        }
        JsonNode valueNode = arrayNode.get(index);
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }
        return valueNode.asLong();
    }
}
