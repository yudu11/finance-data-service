package com.example.financedataservice.client;

public class FinanceDataClientException extends RuntimeException {

    public FinanceDataClientException(String message) {
        super(message);
    }

    public FinanceDataClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
