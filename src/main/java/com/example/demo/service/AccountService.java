package com.example.demo.service;

import com.example.demo.dto.request.CreateAccountRequest;
import com.example.demo.dto.response.AccountResponse;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public interface AccountService {

    AccountResponse createAccount(
        @Valid CreateAccountRequest request);

    AccountResponse getAccountById(Long accountId);

    AccountResponse getAccountByNumber(
            String accountNumber);

    List<AccountResponse> getAccountsByCustomerId(
            Long customerId);

    List<AccountResponse> getAllAccounts();

    BigDecimal getAccountBalance(Long accountId);
}