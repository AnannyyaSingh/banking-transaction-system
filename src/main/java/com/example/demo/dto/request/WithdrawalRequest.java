package com.example.demo.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record WithdrawalRequest(

    @NotNull(message = "Account ID is required")
    @Positive(message = "Invalid account ID")
    Long accountId,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    @Digits(integer = 13, fraction = 2,
            message = "Invalid amount precision")
    BigDecimal amount,

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100)
    String idempotencyKey,

    String description

) {}