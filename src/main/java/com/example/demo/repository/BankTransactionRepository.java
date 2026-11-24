
package com.example.demo.repository;

import com.example.demo.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
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
}