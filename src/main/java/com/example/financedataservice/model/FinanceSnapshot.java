package com.example.financedataservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FinanceSnapshot {

    @JsonProperty("snapshotDate")
    private LocalDate snapshotDate;

    @JsonProperty("gold")
    private PriceData gold;

    @JsonProperty("goldHistory")
    private List<PriceData> goldHistory = new ArrayList<>();

    @JsonProperty("stocks")
    private Map<String, List<PriceData>> stocks = new HashMap<>();

    public FinanceSnapshot() {
        // Jackson default constructor
    }

    public FinanceSnapshot(LocalDate snapshotDate, List<PriceData> goldHistory, Map<String, List<PriceData>> stocks) {
        this.snapshotDate = snapshotDate;
        setGoldHistory(goldHistory);
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
        if ((this.goldHistory == null || this.goldHistory.isEmpty()) && gold != null) {
            this.goldHistory = new ArrayList<>();
            this.goldHistory.add(gold);
        }
    }

    public Map<String, List<PriceData>> getStocks() {
        return Collections.unmodifiableMap(stocks);
    }

    public void setStocks(Map<String, List<PriceData>> stocks) {
        this.stocks = stocks == null ? new HashMap<>() : new HashMap<>(stocks);
    }

    public List<PriceData> getGoldHistory() {
        return Collections.unmodifiableList(goldHistory);
    }

    public void setGoldHistory(List<PriceData> goldHistory) {
        List<PriceData> copy = goldHistory == null ? new ArrayList<>() : new ArrayList<>(goldHistory);
        copy.sort(Comparator.comparing(PriceData::getDate));
        this.goldHistory = copy;
        this.gold = copy.isEmpty() ? null : copy.get(copy.size() - 1);
    }
}
