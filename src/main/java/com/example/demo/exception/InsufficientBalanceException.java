package com.example.demo.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends BankingException {

    public InsufficientBalanceException(
            Long accountId,
            BigDecimal availableBalance,
            BigDecimal requestedAmount) {

        super("Insufficient balance in account ID: " + accountId
                + ". Available balance: " + availableBalance
                + ", Requested amount: " + requestedAmount);
    }
}