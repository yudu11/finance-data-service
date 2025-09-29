package com.example.financedataservice.controller;

import com.example.financedataservice.model.PriceData;
import com.example.financedataservice.service.FinanceDataService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PriceDataController {

    private final FinanceDataService financeDataService;

    public PriceDataController(FinanceDataService financeDataService) {
        this.financeDataService = financeDataService;
    }

    @GetMapping("/getPriceData")
    public ResponseEntity<List<PriceData>> getPriceData(@RequestParam("symbol") String symbol) {
        if (!StringUtils.hasText(symbol)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol query parameter is required");
        }

        List<PriceData> priceData = financeDataService.getPriceDataForSymbol(symbol);
        if (priceData.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No data available for symbol " + symbol.toUpperCase());
        }

        return ResponseEntity.ok(priceData);
    }

    @GetMapping("/symbols")
    public ResponseEntity<List<String>> getAvailableSymbols() {
        List<String> symbols = financeDataService.getAvailableSymbols();
        return ResponseEntity.ok(symbols);
    }
}
