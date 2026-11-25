package com.example.demo.exception;

public class DuplicateTransactionException extends BankingException {

    public DuplicateTransactionException(String idempotencyKey) {
        super("Conflicting transaction request with idempotency key: "
                + idempotencyKey);
    }
}