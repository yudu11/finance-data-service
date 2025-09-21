package com.example.financedataservice.client;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class YahooFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public YahooFinanceClient(RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper,
                              @Value("${yahoo-finance.base-url}") String baseUrl) {
        this(restTemplateBuilder.rootUri(baseUrl).build(), objectMapper);
    }

    YahooFinanceClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PriceData> fetchHistoricalPrices(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must be provided");
        }
        if (days <= 0) {
            throw new IllegalArgumentException("Days parameter must be greater than zero");
        }

        var uri = UriComponentsBuilder.fromUriString(String.format("/v8/finance/chart/%s", symbol))
            .queryParam("range", days + "d")
            .queryParam("interval", "1d")
            .build(true)
            .toUri();

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new FinanceDataClientException("Failed to fetch stock prices for " + symbol);
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
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
