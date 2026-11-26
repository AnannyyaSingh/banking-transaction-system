package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TransferRequest(

    @NotNull(message = "Source account ID is required")
    @Positive(message = "Invalid source account ID")
    Long sourceAccountId,

    @NotNull(message = "Destination account ID is required")
    @Positive(message = "Invalid destination account ID")
    Long destinationAccountId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Digits(integer = 13, fraction = 2,
            message = "Invalid amount precision")
    BigDecimal amount,

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100)
    String idempotencyKey,

    @Size(max = 255,
        message = "Description cannot exceed 255 characters")
    String description

) {}