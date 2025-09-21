package com.example.financedataservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class StockConfig {

    private final StockConfiguration stockConfiguration;

    public StockConfig(ObjectMapper objectMapper,
                       @Value("classpath:config/stocks.json") Resource configResource) {
        this.stockConfiguration = loadConfiguration(objectMapper, configResource);
    }

    private StockConfiguration loadConfiguration(ObjectMapper objectMapper, Resource configResource) {
        if (!configResource.exists()) {
            throw new IllegalStateException("Required stock configuration file is missing: " + configResource);
        }

        try (InputStream inputStream = configResource.getInputStream()) {
            StockConfiguration configuration = objectMapper.readValue(inputStream, StockConfiguration.class);
            validate(configuration);
            return configuration;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load stock configuration", e);
        }
    }

    private void validate(StockConfiguration configuration) {
        List<String> symbols = configuration.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalStateException("Stock configuration must contain at least one symbol");
        }

        boolean hasBlankSymbol = symbols.stream().anyMatch(symbol -> symbol == null || symbol.isBlank());
        if (hasBlankSymbol) {
            throw new IllegalStateException("Stock configuration contains invalid symbol entries");
        }

        if (configuration.getDays() <= 0) {
            throw new IllegalStateException("Stock configuration days must be greater than zero");
        }

        if (configuration.getGoldDays() <= 0) {
            throw new IllegalStateException("Stock configuration goldDays must be greater than zero");
        }
    }

    public List<String> getSymbols() {
        return stockConfiguration.getSymbols();
    }

    public int getDays() {
        return stockConfiguration.getDays();
    }

    public int getGoldDays() {
        return stockConfiguration.getGoldDays();
    }

    public StockConfiguration getStockConfiguration() {
        return stockConfiguration;
    }
}
