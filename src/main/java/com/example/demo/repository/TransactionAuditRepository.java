
package com.example.demo.repository;

import com.example.demo.entity.TransactionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionAuditRepository
        extends JpaRepository<TransactionAudit, Long> {

    List<TransactionAudit>
        findByTransaction_TransactionIdOrderByCreatedAtAscAuditIdAsc(
            Long transactionId);

    List<TransactionAudit> findByIdempotencyKey(
            String idempotencyKey);

    List<TransactionAudit> findByEventType(
            String eventType);
}