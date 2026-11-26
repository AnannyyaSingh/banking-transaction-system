package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;

import com.example.demo.entity.Account;
import com.example.demo.entity.BankTransaction;
import com.example.demo.entity.TransactionAudit;

import com.example.demo.enums.TransactionType;
import com.example.demo.enums.TransactionStatus;
import com.example.demo.enums.AuditEventType;

import com.example.demo.exception.AccountNotFoundException;

import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.BankTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;

import com.example.demo.validation.TransactionValidator;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TransactionExecutionService {

    private final AccountRepository accountRepository;

    private final BankTransactionRepository
            bankTransactionRepository;

    private final TransactionAuditRepository
            transactionAuditRepository;

    private final TransactionValidator transactionValidator;

    private final IdempotencyService idempotencyService;

    private final EntityManager entityManager;

    // Constructor-based dependency injection
    public TransactionExecutionService(
            AccountRepository accountRepository,
            BankTransactionRepository bankTransactionRepository,
            TransactionAuditRepository transactionAuditRepository,
            TransactionValidator transactionValidator,
            IdempotencyService idempotencyService,
            EntityManager entityManager) {

        this.accountRepository = accountRepository;

        this.bankTransactionRepository =
                bankTransactionRepository;

        this.transactionAuditRepository =
                transactionAuditRepository;

        this.transactionValidator = transactionValidator;

        this.idempotencyService = idempotencyService;

        this.entityManager = entityManager;
    }

    // ================================================
    // DEPOSIT OPERATION
    // ================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BankTransaction executeDeposit(
            DepositRequest request) {

        // Step 1: Validate request
        transactionValidator.validateAccountId(
                request.accountId());

        validateCommonFields(
                request.amount(),
                request.idempotencyKey(),
                request.description());

        // Step 2: First idempotency check
        Optional<BankTransaction> existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.DEPOSIT,
                        null,
                        request.accountId(),
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 3: Acquire account lock
        Account account = lockAccount(
                request.accountId());

        // Step 4: Recheck idempotency after locking
        existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.DEPOSIT,
                        null,
                        request.accountId(),
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 5: Validate locked account
        transactionValidator.validateAccountStatus(account);

        transactionValidator.validateBalanceLimit(
                account, request.amount());

        // Step 6: Create pending transaction
        BankTransaction transaction =
                createPendingTransaction(
                        request.idempotencyKey(),
                        TransactionType.DEPOSIT,
                        null,
                        account,
                        request.amount(),
                        request.description()
                );

        // Step 7: Credit account balance
        account.setBalance(
                account.getBalance().add(request.amount()));

        accountRepository.save(account);

        // Step 8: Complete transaction
        return completeTransaction(
                transaction,
                "Deposit completed successfully");
    }

    // ================================================
    // WITHDRAWAL OPERATION
    // ================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BankTransaction executeWithdrawal(
            WithdrawalRequest request) {

        // Step 1: Validate request
        transactionValidator.validateAccountId(
                request.accountId());

        validateCommonFields(
                request.amount(),
                request.idempotencyKey(),
                request.description());

        // Step 2: First idempotency check
        Optional<BankTransaction> existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.WITHDRAWAL,
                        request.accountId(),
                        null,
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 3: Acquire account lock
        Account account = lockAccount(
                request.accountId());

        // Step 4: Recheck idempotency after locking
        existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.WITHDRAWAL,
                        request.accountId(),
                        null,
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 5: Validate locked account
        transactionValidator.validateAccountStatus(account);

        transactionValidator.validateSufficientBalance(
                account, request.amount());

        // Step 6: Create pending transaction
        BankTransaction transaction =
                createPendingTransaction(
                        request.idempotencyKey(),
                        TransactionType.WITHDRAWAL,
                        account,
                        null,
                        request.amount(),
                        request.description()
                );

        // Step 7: Debit account balance
        account.setBalance(
                account.getBalance().subtract(request.amount()));

        accountRepository.save(account);

        // Step 8: Complete transaction
        return completeTransaction(
                transaction,
                "Withdrawal completed successfully");
    }

    // ================================================
    // FUND TRANSFER OPERATION
    // ================================================

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BankTransaction executeTransfer(
            TransferRequest request) {

        // Step 1: Validate request
        transactionValidator.validateDifferentAccounts(
                request.sourceAccountId(),
                request.destinationAccountId());

        validateCommonFields(
                request.amount(),
                request.idempotencyKey(),
                request.description());

        // Step 2: First idempotency check
        Optional<BankTransaction> existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.TRANSFER,
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 3: Determine consistent locking order
        Long firstAccountId = Math.min(
                request.sourceAccountId(),
                request.destinationAccountId());

        Long secondAccountId = Math.max(
                request.sourceAccountId(),
                request.destinationAccountId());

        // Step 4: Acquire locks in ascending ID order
        Account firstAccount =
                lockAccount(firstAccountId);

        Account secondAccount =
                lockAccount(secondAccountId);

        // Step 5: Recheck idempotency after locking
        existingTransaction =
                idempotencyService.checkExistingTransaction(
                        request.idempotencyKey(),
                        TransactionType.TRANSFER,
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        request.amount(),
                        request.description()
                );

        if (existingTransaction.isPresent()) {
            return existingTransaction.get();
        }

        // Step 6: Identify source and destination accounts
        Account sourceAccount =
                firstAccountId.equals(request.sourceAccountId())
                        ? firstAccount
                        : secondAccount;

        Account destinationAccount =
                firstAccountId.equals(request.destinationAccountId())
                        ? firstAccount
                        : secondAccount;

        // Step 7: Validate both locked accounts
        transactionValidator.validateAccountStatus(
                sourceAccount);

        transactionValidator.validateAccountStatus(
                destinationAccount);

        transactionValidator.validateSufficientBalance(
                sourceAccount, request.amount());

        transactionValidator.validateBalanceLimit(
                destinationAccount, request.amount());

        // Step 8: Create pending transaction
        BankTransaction transaction =
                createPendingTransaction(
                        request.idempotencyKey(),
                        TransactionType.TRANSFER,
                        sourceAccount,
                        destinationAccount,
                        request.amount(),
                        request.description()
                );

        // Step 9: Debit source account
        sourceAccount.setBalance(
                sourceAccount.getBalance()
                        .subtract(request.amount()));

        // Step 10: Credit destination account
        destinationAccount.setBalance(
                destinationAccount.getBalance()
                        .add(request.amount()));

        // Step 11: Save both account changes
        accountRepository.save(firstAccount);

        accountRepository.save(secondAccount);

        // Step 12: Complete transaction
        return completeTransaction(
                transaction,
                "Fund transfer completed successfully");
    }

    // ================================================
    // COMMON REQUEST VALIDATION
    // ================================================

    private void validateCommonFields(
            BigDecimal amount,
            String idempotencyKey,
            String description) {

        transactionValidator.validateAmount(amount);

        transactionValidator.validateIdempotencyKey(
                idempotencyKey);

        transactionValidator.validateDescription(
                description);
    }

    // ================================================
    // ACCOUNT LOCKING HELPER
    // ================================================

    private Account lockAccount(Long accountId) {

        return accountRepository
                .findByIdForUpdate(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));
    }

    // ================================================
    // CREATE PENDING TRANSACTION
    // ================================================

    private BankTransaction createPendingTransaction(
            String idempotencyKey,
            TransactionType transactionType,
            Account sourceAccount,
            Account destinationAccount,
            BigDecimal amount,
            String description) {

        BankTransaction transaction = new BankTransaction();

        transaction.setIdempotencyKey(idempotencyKey);

        transaction.setTransactionType(
                transactionType.name());

        transaction.setSourceAccount(sourceAccount);

        transaction.setDestinationAccount(destinationAccount);

        transaction.setAmount(amount);

        transaction.setDescription(description);

        transaction.setStatus(
                TransactionStatus.PENDING.name());

        // Enforce database unique constraint
        transaction =
                bankTransactionRepository.saveAndFlush(
                        transaction);

        // Record transaction creation
        createAudit(
                transaction,
                AuditEventType.TRANSACTION_CREATED,
                null,
                TransactionStatus.PENDING.name(),
                transactionType.name() + " transaction created"
        );

        // Record successful validation
        createAudit(
                transaction,
                AuditEventType.VALIDATION_COMPLETED,
                TransactionStatus.PENDING.name(),
                TransactionStatus.PENDING.name(),
                transactionType.name() + " validation completed"
        );

        return transaction;
    }

    // ================================================
    // COMPLETE SUCCESSFUL TRANSACTION
    // ================================================

    private BankTransaction completeTransaction(
            BankTransaction transaction,
            String message) {

        transaction.setStatus(
                TransactionStatus.SUCCESS.name());

        transaction.setCompletedAt(
                LocalDateTime.now());

        transaction =
                bankTransactionRepository.saveAndFlush(
                        transaction);

        // Record successful completion
        createAudit(
                transaction,
                AuditEventType.TRANSACTION_COMPLETED,
                TransactionStatus.PENDING.name(),
                TransactionStatus.SUCCESS.name(),
                message
        );

        // Retrieve database-generated timestamp
        entityManager.refresh(transaction);

        return transaction;
    }

    // ================================================
    // REUSABLE AUDIT LOGGING HELPER
    // ================================================

    private void createAudit(
            BankTransaction transaction,
            AuditEventType eventType,
            String previousStatus,
            String newStatus,
            String message) {

        TransactionAudit audit = new TransactionAudit();

        audit.setTransaction(transaction);

        audit.setIdempotencyKey(
                transaction.getIdempotencyKey());

        audit.setEventType(eventType.name());

        audit.setPreviousStatus(previousStatus);

        audit.setNewStatus(newStatus);

        audit.setMessage(message);

        transactionAuditRepository.save(audit);
    }
}