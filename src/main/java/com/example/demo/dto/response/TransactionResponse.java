package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(

    Long transactionId,
    String idempotencyKey,
    String transactionType,
    Long sourceAccountId,
    Long destinationAccountId,
    BigDecimal amount,
    String status,
    String description,
    LocalDateTime createdAt,
    LocalDateTime completedAt

) {}