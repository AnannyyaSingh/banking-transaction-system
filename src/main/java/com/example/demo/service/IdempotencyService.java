package com.example.demo.service;

import com.example.demo.entity.Account;
import com.example.demo.entity.BankTransaction;
import com.example.demo.enums.TransactionType;
import com.example.demo.exception.DuplicateTransactionException;
import com.example.demo.repository.BankTransactionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final BankTransactionRepository
            bankTransactionRepository;

    public IdempotencyService(
            BankTransactionRepository bankTransactionRepository) {

        this.bankTransactionRepository =
                bankTransactionRepository;
    }

    // Check whether a transaction already exists
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<BankTransaction> checkExistingTransaction(
            String idempotencyKey,
            TransactionType transactionType,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String description) {

        Optional<BankTransaction> existingTransaction =
                bankTransactionRepository
                        .findByIdempotencyKey(idempotencyKey);

        if (existingTransaction.isEmpty()) {
            return Optional.empty();
        }

        BankTransaction transaction =
                existingTransaction.get();

        if (!isSameRequest(
                transaction,
                transactionType,
                sourceAccountId,
                destinationAccountId,
                amount,
                description)) {

            throw new DuplicateTransactionException(
                    idempotencyKey);
        }

        return Optional.of(transaction);
    }

    // Compare the existing transaction with the new request
    private boolean isSameRequest(
            BankTransaction transaction,
            TransactionType transactionType,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String description) {

        return transactionType.name()
                    .equals(transaction.getTransactionType())

                && Objects.equals(
                    getAccountId(transaction.getSourceAccount()),
                    sourceAccountId)

                && Objects.equals(
                    getAccountId(transaction.getDestinationAccount()),
                    destinationAccountId)

                && transaction.getAmount().compareTo(amount) == 0

                && Objects.equals(
                    transaction.getDescription(),
                    description);
    }

    // Safely retrieve an account ID
    private Long getAccountId(Account account) {

        return account == null
                ? null
                : account.getAccountId();
    }
}