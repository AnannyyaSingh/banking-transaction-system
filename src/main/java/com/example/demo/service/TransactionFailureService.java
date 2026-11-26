package com.example.demo.service;

import com.example.demo.entity.FailedTransaction;
import com.example.demo.entity.TransactionAudit;

import com.example.demo.enums.AuditEventType;
import com.example.demo.enums.FailureReason;
import com.example.demo.enums.TransactionStatus;

import com.example.demo.repository.FailedTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionFailureService {

    private final FailedTransactionRepository
            failedTransactionRepository;

    private final TransactionAuditRepository
            transactionAuditRepository;

    // Constructor-based dependency injection
    public TransactionFailureService(
            FailedTransactionRepository failedTransactionRepository,
            TransactionAuditRepository transactionAuditRepository) {

        this.failedTransactionRepository =
                failedTransactionRepository;

        this.transactionAuditRepository =
                transactionAuditRepository;
    }

    // ================================================
    // RECORD FAILED TRANSACTION
    // ================================================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String idempotencyKey,
            FailureReason failureReason,
            String failureDescription,
            boolean rejected) {

        // Step 1: Prepare safe database values
        String safeKey = normalizeKey(idempotencyKey);

        String safeDescription =
                truncate(failureDescription, 500);

        // Step 2: Create failed transaction record
        FailedTransaction failedTransaction =
                new FailedTransaction();

        // The original transaction may have been rolled back
        failedTransaction.setTransaction(null);

        failedTransaction.setIdempotencyKey(safeKey);

        failedTransaction.setFailureReason(
                failureReason.name());

        failedTransaction.setFailureDescription(
                safeDescription);

        // Step 3: Save failed transaction
        failedTransactionRepository.save(
                failedTransaction);

        // Step 4: Create audit record
        TransactionAudit audit = new TransactionAudit();

        audit.setTransaction(null);

        audit.setIdempotencyKey(safeKey);

        audit.setPreviousStatus(null);

        if (rejected) {

            audit.setEventType(
                    AuditEventType.TRANSACTION_REJECTED.name());

            audit.setNewStatus(
                    TransactionStatus.REJECTED.name());

        } else {

            audit.setEventType(
                    AuditEventType.TRANSACTION_FAILED.name());

            audit.setNewStatus(
                    TransactionStatus.FAILED.name());
        }

        audit.setMessage(
                truncate(
                        failureReason.name()
                                + ": "
                                + safeDescription,
                        500));

        // Step 5: Save audit record
        transactionAuditRepository.save(audit);
    }

    // ================================================
    // NORMALIZE IDEMPOTENCY KEY FOR FAILURE LOGGING
    // ================================================

    private String normalizeKey(String idempotencyKey) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {

            return null;
        }

        return idempotencyKey;
    }

    // ================================================
    // STRING LENGTH HELPER
    // ================================================

    private String truncate(String value, int maxLength) {

        if (value == null) {
            return null;
        }

        int length = value.codePointCount(0, value.length());

        if (length <= maxLength) {
            return value;
        }

        return value.substring(
                0, value.offsetByCodePoints(0, maxLength));
    }
}