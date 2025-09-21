package com.example.financedataservice.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StockConfiguration {

    @JsonProperty("symbols")
    private List<String> symbols = new ArrayList<>();

    @JsonProperty("days")
    private int days = 30;

    @JsonProperty("goldDays")
    private int goldDays = 1;

    public StockConfiguration() {
        // Jackson default constructor
    }

    public List<String> getSymbols() {
        return Collections.unmodifiableList(symbols);
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols == null ? new ArrayList<>() : new ArrayList<>(symbols);
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getGoldDays() {
        return goldDays;
    }

    public void setGoldDays(int goldDays) {
        this.goldDays = goldDays;
    }
}
