package com.example.financedataservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinanceSnapshot {

    @JsonProperty("snapshotDate")
    private LocalDate snapshotDate;

    @JsonProperty("gold")
    private PriceData gold;

    @JsonProperty("stocks")
    private Map<String, List<PriceData>> stocks = new HashMap<>();

    public FinanceSnapshot() {
        // Jackson default constructor
    }

    public FinanceSnapshot(LocalDate snapshotDate, PriceData gold, Map<String, List<PriceData>> stocks) {
        this.snapshotDate = snapshotDate;
        this.gold = gold;
        setStocks(stocks);
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public PriceData getGold() {
        return gold;
    }

    public void setGold(PriceData gold) {
        this.gold = gold;
    }

    public Map<String, List<PriceData>> getStocks() {
        return Collections.unmodifiableMap(stocks);
    }

    public void setStocks(Map<String, List<PriceData>> stocks) {
        this.stocks = stocks == null ? new HashMap<>() : new HashMap<>(stocks);
    }
}
