package com.example.demo.validation;

import com.example.demo.entity.Account;
import com.example.demo.enums.AccountStatus;
import com.example.demo.exception.InactiveAccountException;
import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.exception.InvalidTransactionException;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionValidator {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("9999999999999.99");

    // 1. Validate account ID
    public void validateAccountId(Long accountId) {

        if (accountId == null || accountId <= 0) {
            throw new InvalidTransactionException(
                    "Account ID must be a positive number");
        }
    }

    // 2. Validate transaction amount
    public void validateAmount(BigDecimal amount) {

        if (amount == null) {
            throw new InvalidTransactionException(
                    "Transaction amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransactionException(
                    "Transaction amount must be greater than zero");
        }

        if (amount.scale() > 2) {
            throw new InvalidTransactionException(
                    "Transaction amount cannot have more than two decimal places");
        }

        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidTransactionException(
                    "Transaction amount exceeds the maximum allowed limit");
        }
    }

    // 3. Validate idempotency key
    public void validateIdempotencyKey(String idempotencyKey) {

        if (idempotencyKey == null ||
                idempotencyKey.isBlank()) {

            throw new InvalidTransactionException(
                    "Idempotency key is required");
        }

        if (idempotencyKey.length() > 100) {
            throw new InvalidTransactionException(
                    "Idempotency key cannot exceed 100 characters");
        }
    }

    // 4. Validate account status
    public void validateAccountStatus(Account account) {

        if (!AccountStatus.ACTIVE.name()
                .equals(account.getAccountStatus())) {

            throw new InactiveAccountException(
                    account.getAccountId(),
                    account.getAccountStatus()
            );
        }
    }

    // 5. Validate sufficient balance
    public void validateSufficientBalance(
            Account account, BigDecimal amount) {

        if (account.getBalance().compareTo(amount) < 0) {

            throw new InsufficientBalanceException(
                    account.getAccountId(),
                    account.getBalance(),
                    amount
            );
        }
    }

    // 6. Prevent same-account transfers
    public void validateDifferentAccounts(
            Long sourceAccountId,
            Long destinationAccountId) {

        validateAccountId(sourceAccountId);
        validateAccountId(destinationAccountId);

        if (sourceAccountId.equals(destinationAccountId)) {

            throw new InvalidTransactionException(
                    "Source and destination accounts must be different");
        }
    }

    // 7. Prevent balance overflow
    public void validateBalanceLimit(
            Account account, BigDecimal amount) {

        BigDecimal resultingBalance =
                account.getBalance().add(amount);

        if (resultingBalance.compareTo(MAX_AMOUNT) > 0) {

            throw new InvalidTransactionException(
                    "Resulting account balance exceeds the maximum allowed limit");
        }
    }

    // 8. Validate transaction description
    public void validateDescription(String description) {

        if (description != null &&
                description.length() > 255) {

            throw new InvalidTransactionException(
                    "Description cannot exceed 255 characters");
        }
    }
}