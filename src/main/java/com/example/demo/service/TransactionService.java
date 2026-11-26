package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;

import com.example.demo.dto.response.TransactionResponse;
import com.example.demo.dto.response.TransactionAuditResponse;
import com.example.demo.dto.response.FailedTransactionResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface TransactionService {

    TransactionResponse deposit(
            @NotNull @Valid DepositRequest request);

    TransactionResponse withdraw(
            @NotNull @Valid WithdrawalRequest request);

    TransactionResponse transfer(
            @NotNull @Valid TransferRequest request);

    TransactionResponse getTransactionById(
            Long transactionId);

    TransactionResponse getTransactionByIdempotencyKey(
            String idempotencyKey);

    List<TransactionResponse> getTransactionsByAccountId(
            Long accountId);

    List<TransactionResponse> getAllTransactions();

    List<TransactionAuditResponse> getTransactionAuditHistory(
            Long transactionId);

    List<FailedTransactionResponse> getFailedTransactions();
}