package com.example.demo.exception;

public class DuplicateCustomerException extends BankingException {

    public DuplicateCustomerException(String message) {
        super(message);
    }
}