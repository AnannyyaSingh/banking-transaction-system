package com.example.demo.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateAccountRequest(

    @NotNull(message = "Customer ID is required")
    @Positive(message = "Invalid customer ID")
    Long customerId

) {}