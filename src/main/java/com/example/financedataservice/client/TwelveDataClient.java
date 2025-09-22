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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
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
public class TwelveDataClient {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataClient.class);
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(2);
    private static final int MAX_RETRIES = 3;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String apiKey;
    private final String interval;

    @Autowired
    public TwelveDataClient(RestTemplateBuilder restTemplateBuilder,
                            ObjectMapper objectMapper,
                            @Value("${twelve-data.base-url}") String baseUrl,
                            @Value("${twelve-data.api-key}") String apiKey,
                            @Value("${twelve-data.interval:1day}") String interval) {
        this(restTemplateBuilder.rootUri(baseUrl).build(), objectMapper, Clock.systemUTC(), apiKey, interval);
    }

    TwelveDataClient(RestTemplate restTemplate,
                     ObjectMapper objectMapper,
                     Clock clock,
                     String apiKey,
                     String interval) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Twelve Data API key must be provided");
        }
        this.apiKey = apiKey;
        this.interval = (interval == null || interval.isBlank()) ? "1day" : interval;
    }

    public List<PriceData> fetchHistoricalPrices(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Days parameter must be greater than zero");
        }

        LocalDate endDate = LocalDate.now(clock);
        LocalDate startDate = endDate.minusDays(days);

        String uri = UriComponentsBuilder.fromPath("/time_series")
            .queryParam("symbol", symbol)
            .queryParam("interval", interval)
            .queryParam("start_date", DATE_FORMATTER.format(startDate))
            .queryParam("end_date", DATE_FORMATTER.format(endDate))
            .queryParam("apikey", apiKey)
            .build(true)
            .toUriString();

        try {
            String body = fetchWithRetry(symbol, uri);
            return parseResponse(symbol, body);
        } catch (IOException e) {
            log.error("Failed to parse Twelve Data response for symbol {}", symbol, e);
            throw new FinanceDataClientException("Failed to parse Twelve Data response", e);
        }
    }

    private List<PriceData> parseResponse(String symbol, String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        String status = root.path("status").asText();
        if (!"ok".equalsIgnoreCase(status)) {
            String message = root.path("message").asText("Unknown Twelve Data error");
            throw new FinanceDataClientException("Twelve Data reported error: " + message);
        }

        JsonNode valuesNode = root.path("values");
        if (!valuesNode.isArray()) {
            throw new FinanceDataClientException("Twelve Data response missing values array");
        }

        List<PriceData> dataPoints = new ArrayList<>();
        for (JsonNode valueNode : valuesNode) {
            if (valueNode == null || !valueNode.isObject()) {
                continue;
            }

            LocalDate date = parseDate(valueNode.path("datetime"));
            BigDecimal open = parseDecimal(valueNode.path("open"));
            BigDecimal high = parseDecimal(valueNode.path("high"));
            BigDecimal low = parseDecimal(valueNode.path("low"));
            BigDecimal close = parseDecimal(valueNode.path("close"));
            Long volume = parseLong(valueNode.path("volume"));

            dataPoints.add(new PriceData(symbol.toUpperCase(), date, open, high, low, close, volume, PriceDataSource.TWELVE_DATA));
        }

        return dataPoints;
    }

    private LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new FinanceDataClientException("Twelve Data response missing datetime value");
        }
        String raw = node.asText();
        if (raw == null || raw.isBlank()) {
            throw new FinanceDataClientException("Twelve Data response contains blank datetime value");
        }

        try {
            if (raw.length() >= 10) {
                return LocalDate.parse(raw.substring(0, 10));
            }
            return LocalDate.parse(raw, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new FinanceDataClientException("Failed to parse Twelve Data datetime: " + raw, e);
        }
    }

    private BigDecimal parseDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private Long parseLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
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
                    throw new FinanceDataClientException("Twelve Data rate limit exceeded for " + symbol, tooManyRequests);
                }
                Duration delay = resolveRetryDelay(tooManyRequests.getResponseHeaders());
                long sleepMillis = Math.max(delay.toMillis(), DEFAULT_RETRY_DELAY.toMillis());
                log.warn("Twelve Data rate limited for {}. Retrying in {} ms (attempt {}/{})",
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
            throw new FinanceDataClientException("Interrupted while waiting to retry Twelve Data request", interruptedException);
        }
    }
}
