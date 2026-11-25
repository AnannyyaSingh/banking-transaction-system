package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record FailedTransactionResponse(

    Long failedId,
    Long transactionId,
    String idempotencyKey,
    String failureReason,
    String failureDescription,
    LocalDateTime failedAt

) {}