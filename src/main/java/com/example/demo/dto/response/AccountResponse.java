package com.example.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(

    Long accountId,
    Long customerId,
    String accountNumber,
    BigDecimal balance,
    String accountStatus,
    LocalDateTime createdAt

) {}