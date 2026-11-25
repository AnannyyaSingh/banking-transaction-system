package com.example.demo.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(

    @NotBlank(message = "Full name is required")
    @Size(max = 100,
          message = "Full name cannot exceed 100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 100,
          message = "Email cannot exceed 100 characters")
    String email,

    @NotBlank(message = "Phone number is required")
    @Size(max = 15,
          message = "Phone number cannot exceed 15 characters")
    String phone

) {}