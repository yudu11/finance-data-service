package com.example.financedataservice.client;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.model.PriceDataSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AlphaVantageClient {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageClient.class);

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    @Autowired
    public AlphaVantageClient(RestTemplateBuilder restTemplateBuilder,
                              ObjectMapper objectMapper,
                              @Value("${alpha-vantage.base-url}") String baseUrl,
                              @Value("${alpha-vantage.api-key}") String apiKey) {
        this(restTemplateBuilder.rootUri(baseUrl).build(), objectMapper, apiKey);
    }

    AlphaVantageClient(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public List<PriceData> fetchGoldPriceHistory(int days) {
        if (days <= 0) {
            throw new IllegalArgumentException("Days parameter must be greater than zero");
        }

        String uri = UriComponentsBuilder.fromPath("/query")
            .queryParam("function", "TIME_SERIES_DAILY")
            .queryParam("symbol", "XAUUSD")
            .queryParam("outputsize", "full")
            .queryParam("datatype", "json")
            .queryParam("apikey", apiKey)
            .build(true)
            .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new FinanceDataClientException("Failed to fetch gold price from AlphaVantage");
        }

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode series = root.path("Time Series (Daily)");
            if (series.isMissingNode()) {
                throw new FinanceDataClientException("AlphaVantage response missing time series data");
            }

            Iterator<Map.Entry<String, JsonNode>> iterator = series.fields();
            List<PriceData> prices = new ArrayList<>();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                LocalDate date = LocalDate.parse(entry.getKey());
                JsonNode values = entry.getValue();

                PriceData priceData = toPriceData(date, values);
                prices.add(priceData);
            }

            if (prices.isEmpty()) {
                throw new FinanceDataClientException("AlphaVantage response did not contain date entries");
            }

            return prices.stream()
                .sorted(Comparator.comparing(PriceData::getDate).reversed())
                .limit(days)
                .sorted(Comparator.comparing(PriceData::getDate))
                .toList();
        } catch (IOException e) {
            log.error("Failed to parse AlphaVantage response", e);
            throw new FinanceDataClientException("Failed to parse AlphaVantage response", e);
        }
    }

    public PriceData fetchLatestGoldPrice() {
        return fetchGoldPriceHistory(1).stream()
            .findFirst()
            .orElseThrow(() -> new FinanceDataClientException("AlphaVantage response did not contain date entries"));
    }

    private BigDecimal readDecimal(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        if (valueNode == null || valueNode.isNull()) {
            throw new FinanceDataClientException("AlphaVantage response missing field: " + fieldName);
        }
        return new BigDecimal(valueNode.asText());
    }

    private PriceData toPriceData(LocalDate date, JsonNode values) {
        BigDecimal open = readDecimal(values, "1. open");
        BigDecimal high = readDecimal(values, "2. high");
        BigDecimal low = readDecimal(values, "3. low");
        BigDecimal close = readDecimal(values, "4. close");

        return new PriceData("XAUUSD", date, open, high, low, close, null, PriceDataSource.GOLD);
    }
}
