
package com.example.demo.repository;

import com.example.demo.entity.FailedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FailedTransactionRepository
        extends JpaRepository<FailedTransaction, Long> {

    List<FailedTransaction> findByFailureReason(
            String failureReason);

    List<FailedTransaction> findByIdempotencyKey(
            String idempotencyKey);

    List<FailedTransaction> findByTransaction_TransactionId(
            Long transactionId);
}