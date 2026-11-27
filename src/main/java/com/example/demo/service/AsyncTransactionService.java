
package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;
import com.example.demo.dto.response.TransactionResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AsyncTransactionService {

    private static final Logger logger =
            LoggerFactory.getLogger(AsyncTransactionService.class);

    private final TransactionService transactionService;

    public AsyncTransactionService(
            TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Async("transactionExecutor")
    public CompletableFuture<TransactionResponse> depositAsync(
            DepositRequest request) {

        logger.info("Processing async deposit on thread: {}",
                Thread.currentThread().getName());

        TransactionResponse response =
                transactionService.deposit(request);

        return CompletableFuture.completedFuture(response);
    }

    @Async("transactionExecutor")
    public CompletableFuture<TransactionResponse> withdrawAsync(
            WithdrawalRequest request) {

        logger.info("Processing async withdrawal on thread: {}",
                Thread.currentThread().getName());

        TransactionResponse response =
                transactionService.withdraw(request);

        return CompletableFuture.completedFuture(response);
    }

    @Async("transactionExecutor")
    public CompletableFuture<TransactionResponse> transferAsync(
            TransferRequest request) {

        logger.info("Processing async transfer on thread: {}",
                Thread.currentThread().getName());

        TransactionResponse response =
                transactionService.transfer(request);

        return CompletableFuture.completedFuture(response);
    }
}
