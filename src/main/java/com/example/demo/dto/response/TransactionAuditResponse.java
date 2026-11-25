package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record TransactionAuditResponse(

    Long auditId,
    Long transactionId,
    String idempotencyKey,
    String eventType,
    String previousStatus,
    String newStatus,
    String message,
    LocalDateTime createdAt

) {}