package com.example.demo.repository;

import com.example.demo.entity.BankTransaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByIdempotencyKey(
            String idempotencyKey);

    boolean existsByIdempotencyKey(
            String idempotencyKey);

    List<BankTransaction> findByStatus(
            String status);

    List<BankTransaction> findByTransactionType(
            String transactionType);

    List<BankTransaction> findBySourceAccount_AccountId(
            Long accountId);

    List<BankTransaction> findByDestinationAccount_AccountId(
            Long accountId);

    @Query("""
            SELECT t FROM BankTransaction t
            LEFT JOIN t.sourceAccount s
            LEFT JOIN t.destinationAccount d
            WHERE s.accountId = :accountId
               OR d.accountId = :accountId
            ORDER BY t.createdAt DESC,
                     t.transactionId DESC
            """)
    List<BankTransaction> findTransactionsByAccountId(
            @Param("accountId") Long accountId);
}