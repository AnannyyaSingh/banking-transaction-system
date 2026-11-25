package com.example.demo.exception;

public class InactiveAccountException extends BankingException {

    public InactiveAccountException(Long accountId, String status) {
        super("Account ID: " + accountId
                + " is not active. Current status: " + status);
    }
}