package com.example.demo.dto.response;

import java.time.LocalDateTime;

public record CustomerResponse(

    Long customerId,
    String fullName,
    String email,
    String phone,
    LocalDateTime createdAt

) {}