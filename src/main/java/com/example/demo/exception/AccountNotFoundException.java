package com.example.demo.exception;

public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(Long accountId) {
        super("Account not found with ID: " + accountId);
    }
}