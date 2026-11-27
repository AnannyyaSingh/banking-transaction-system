package com.example.demo.service.impl;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.*;

import com.example.demo.entity.Account;
import com.example.demo.entity.BankTransaction;
import com.example.demo.entity.TransactionAudit;
import com.example.demo.entity.FailedTransaction;

import com.example.demo.enums.FailureReason;
import com.example.demo.enums.TransactionType;

import com.example.demo.exception.*;

import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.BankTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;
import com.example.demo.repository.FailedTransactionRepository;

import com.example.demo.service.TransactionService;
import com.example.demo.service.TransactionExecutionService;
import com.example.demo.service.TransactionFailureService;
import com.example.demo.service.IdempotencyService;
import com.example.demo.service.TransactionRetryService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

@Service
@Validated
@Transactional(readOnly = true)
public class TransactionServiceImpl implements TransactionService {

    private static final Logger logger =
            LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionExecutionService executionService;
    private final TransactionFailureService failureService;
    private final IdempotencyService idempotencyService;
    private final TransactionRetryService retryService;

    private final AccountRepository accountRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final TransactionAuditRepository transactionAuditRepository;
    private final FailedTransactionRepository failedTransactionRepository;

    private final TransactionTemplate recoveryTemplate;

    // Constructor-based dependency injection
    public TransactionServiceImpl(
            TransactionExecutionService executionService,
            TransactionFailureService failureService,
            IdempotencyService idempotencyService,
            TransactionRetryService retryService,
            AccountRepository accountRepository,
            BankTransactionRepository bankTransactionRepository,
            TransactionAuditRepository transactionAuditRepository,
            FailedTransactionRepository failedTransactionRepository,
            PlatformTransactionManager transactionManager) {

        this.executionService = executionService;
        this.failureService = failureService;
        this.idempotencyService = idempotencyService;
        this.retryService = retryService;

        this.accountRepository = accountRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.transactionAuditRepository = transactionAuditRepository;
        this.failedTransactionRepository = failedTransactionRepository;

        this.recoveryTemplate =
                new TransactionTemplate(transactionManager);

        this.recoveryTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.recoveryTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);

        this.recoveryTemplate.setReadOnly(true);
    }

    // ================================================
    // DEPOSIT OPERATION
    // ================================================

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse deposit(DepositRequest request) {

        return executeTransaction(
                request.idempotencyKey(),
                TransactionType.DEPOSIT,
                null,
                request.accountId(),
                request.amount(),
                request.description(),
                () -> executionService.executeDeposit(request)
        );
    }

    // ================================================
    // WITHDRAWAL OPERATION
    // ================================================

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse withdraw(WithdrawalRequest request) {

        return executeTransaction(
                request.idempotencyKey(),
                TransactionType.WITHDRAWAL,
                request.accountId(),
                null,
                request.amount(),
                request.description(),
                () -> executionService.executeWithdrawal(request)
        );
    }

    // ================================================
    // FUND TRANSFER OPERATION
    // ================================================

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransactionResponse transfer(TransferRequest request) {

        return executeTransaction(
                request.idempotencyKey(),
                TransactionType.TRANSFER,
                request.sourceAccountId(),
                request.destinationAccountId(),
                request.amount(),
                request.description(),
                () -> executionService.executeTransfer(request)
        );
    }

    // ================================================
    // COMMON TRANSACTION COORDINATION
    // ================================================

    private TransactionResponse executeTransaction(
            String idempotencyKey,
            TransactionType transactionType,
            Long sourceAccountId,
            Long destinationAccountId,
            BigDecimal amount,
            String description,
            Supplier<BankTransaction> operation) {

        try {

            BankTransaction transaction =
                    retryService.executeWithRetry(
                            transactionType.name(),
                            operation
                    );

            return toTransactionResponse(transaction);

        } catch (DataIntegrityViolationException exception) {

            // The original transaction has already rolled back.
            // Check whether another request committed the same key.

            try {

                TransactionResponse existingResponse =
                        recoveryTemplate.execute(status ->
                                idempotencyService
                                        .checkExistingTransaction(
                                                idempotencyKey,
                                                transactionType,
                                                sourceAccountId,
                                                destinationAccountId,
                                                amount,
                                                description)
                                        .map(this::toTransactionResponse)
                                        .orElse(null)
                        );

                if (existingResponse != null) {

                    return existingResponse;
                }

            } catch (DuplicateTransactionException conflict) {

                recordFailureSafely(
                        idempotencyKey,
                        FailureReason.DUPLICATE_REQUEST,
                        conflict.getMessage(),
                        true,
                        conflict);

                throw conflict;

            } catch (RuntimeException recoveryException) {

                exception.addSuppressed(recoveryException);
            }

            recordFailureSafely(
                    idempotencyKey,
                    FailureReason.DATABASE_ERROR,
                    "Database integrity constraint violation",
                    false,
                    exception);

            throw exception;

        } catch (RuntimeException exception) {

            FailureReason failureReason =
                    determineFailureReason(exception);

            boolean rejected =
                    exception instanceof BankingException;

            String failureDescription = rejected
                    ? exception.getMessage()
                    : "Transaction execution failed: "
                            + failureReason.name();

            recordFailureSafely(
                    idempotencyKey,
                    failureReason,
                    failureDescription,
                    rejected,
                    exception);

            throw exception;
        }
    }

    // ================================================
    // SAFE FAILURE LOGGING
    // ================================================

    private void recordFailureSafely(
            String idempotencyKey,
            FailureReason failureReason,
            String description,
            boolean rejected,
            RuntimeException originalException) {

        try {

            failureService.recordFailure(
                    idempotencyKey,
                    failureReason,
                    description,
                    rejected);

        } catch (RuntimeException loggingException) {

            // Preserve the original transaction exception.
            originalException.addSuppressed(loggingException);

            logger.error(
                    "Unable to persist transaction failure record",
                    loggingException);
        }
    }

    // ================================================
    // FAILURE REASON MAPPING
    // ================================================

    private FailureReason determineFailureReason(
            RuntimeException exception) {

        if (exception instanceof AccountNotFoundException) {

            return FailureReason.ACCOUNT_NOT_FOUND;
        }

        if (exception instanceof InsufficientBalanceException) {

            return FailureReason.INSUFFICIENT_BALANCE;
        }

        if (exception instanceof InactiveAccountException) {

            String message = exception.getMessage();

            if (message != null
                    && message.toUpperCase(java.util.Locale.ROOT)
                            .contains("BLOCKED")) {

                return FailureReason.ACCOUNT_BLOCKED;
            }

            return FailureReason.ACCOUNT_INACTIVE;
        }

        if (exception instanceof DuplicateTransactionException) {

            return FailureReason.DUPLICATE_REQUEST;
        }

        if (exception instanceof InvalidTransactionException) {

            String message = exception.getMessage();

            if (message != null
                    && message.toLowerCase(java.util.Locale.ROOT)
                            .contains("amount")) {

                return FailureReason.INVALID_AMOUNT;
            }

            return FailureReason.INVALID_ACCOUNT_COMBINATION;
        }

        // MySQL deadlock error code: 1213
        Throwable cause = exception;

        while (cause != null) {

            if (cause instanceof java.sql.SQLException sqlException
                    && sqlException.getErrorCode() == 1213) {

                return FailureReason.DEADLOCK_DETECTED;
            }

            cause = cause.getCause();
        }

        if (exception instanceof PessimisticLockingFailureException) {

            return FailureReason.CONCURRENCY_CONFLICT;
        }

        if (exception instanceof DataAccessException) {

            return FailureReason.DATABASE_ERROR;
        }

        return FailureReason.INTERNAL_ERROR;
    }

    // ================================================
    // GET TRANSACTION BY ID
    // ================================================

    @Override
    public TransactionResponse getTransactionById(
            Long transactionId) {

        BankTransaction transaction =
                bankTransactionRepository.findById(transactionId)
                        .orElseThrow(() ->
                                new BankingException(
                                        "Transaction not found with ID: "
                                                + transactionId));

        return toTransactionResponse(transaction);
    }

    // ================================================
    // GET TRANSACTION BY IDEMPOTENCY KEY
    // ================================================

    @Override
    public TransactionResponse getTransactionByIdempotencyKey(
            String idempotencyKey) {

        BankTransaction transaction =
                bankTransactionRepository
                        .findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() ->
                                new BankingException(
                                        "Transaction not found with key: "
                                                + idempotencyKey));

        return toTransactionResponse(transaction);
    }

    // ================================================
    // GET ACCOUNT TRANSACTION HISTORY
    // ================================================

    @Override
    public List<TransactionResponse> getTransactionsByAccountId(
            Long accountId) {

        if (!accountRepository.existsById(accountId)) {

            throw new AccountNotFoundException(accountId);
        }

        return bankTransactionRepository
                .findTransactionsByAccountId(accountId)
                .stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    // ================================================
    // GET ALL TRANSACTIONS
    // ================================================

    @Override
    public List<TransactionResponse> getAllTransactions() {

        return bankTransactionRepository.findAll()
                .stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    // ================================================
    // GET TRANSACTION AUDIT HISTORY
    // ================================================

    @Override
    public List<TransactionAuditResponse> getTransactionAuditHistory(
            Long transactionId) {

        if (!bankTransactionRepository.existsById(transactionId)) {

            throw new BankingException(
                    "Transaction not found with ID: "
                            + transactionId);
        }

        return transactionAuditRepository
                .findByTransaction_TransactionIdOrderByCreatedAtAscAuditIdAsc(
                        transactionId)
                .stream()
                .map(this::toAuditResponse)
                .toList();
    }

    // ================================================
    // GET ALL FAILED TRANSACTIONS
    // ================================================

    @Override
    public List<FailedTransactionResponse> getFailedTransactions() {

        return failedTransactionRepository.findAll()
                .stream()
                .map(this::toFailedTransactionResponse)
                .toList();
    }

    // ================================================
    // TRANSACTION ENTITY TO DTO MAPPING
    // ================================================

    private TransactionResponse toTransactionResponse(
            BankTransaction transaction) {

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getIdempotencyKey(),
                transaction.getTransactionType(),
                getAccountId(transaction.getSourceAccount()),
                getAccountId(transaction.getDestinationAccount()),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getCompletedAt()
        );
    }

    // ================================================
    // AUDIT ENTITY TO DTO MAPPING
    // ================================================

    private TransactionAuditResponse toAuditResponse(
            TransactionAudit audit) {

        return new TransactionAuditResponse(
                audit.getAuditId(),
                getTransactionId(audit.getTransaction()),
                audit.getIdempotencyKey(),
                audit.getEventType(),
                audit.getPreviousStatus(),
                audit.getNewStatus(),
                audit.getMessage(),
                audit.getCreatedAt()
        );
    }

    // ================================================
    // FAILED TRANSACTION ENTITY TO DTO MAPPING
    // ================================================

    private FailedTransactionResponse toFailedTransactionResponse(
            FailedTransaction failedTransaction) {

        return new FailedTransactionResponse(
                failedTransaction.getFailedId(),
                getTransactionId(failedTransaction.getTransaction()),
                failedTransaction.getIdempotencyKey(),
                failedTransaction.getFailureReason(),
                failedTransaction.getFailureDescription(),
                failedTransaction.getFailedAt()
        );
    }

    // ================================================
    // NULL-SAFE HELPER METHODS
    // ================================================

    private Long getAccountId(Account account) {

        return account == null
                ? null
                : account.getAccountId();
    }

    private Long getTransactionId(BankTransaction transaction) {

        return transaction == null
                ? null
                : transaction.getTransactionId();
    }
}