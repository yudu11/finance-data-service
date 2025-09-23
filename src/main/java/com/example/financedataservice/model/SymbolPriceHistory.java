package com.example.financedataservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SymbolPriceHistory {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("prices")
    private List<PriceData> prices = new ArrayList<>();

    public SymbolPriceHistory() {
        // Jackson default constructor
    }

    public SymbolPriceHistory(String symbol, List<PriceData> prices) {
        this.symbol = symbol;
        setPrices(prices);
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public List<PriceData> getPrices() {
        return Collections.unmodifiableList(prices);
    }

    public void setPrices(List<PriceData> prices) {
        List<PriceData> copy = prices == null ? new ArrayList<>() : new ArrayList<>(prices);
        copy.sort(Comparator.comparing(PriceData::getDate));
        this.prices = copy;
    }
}
