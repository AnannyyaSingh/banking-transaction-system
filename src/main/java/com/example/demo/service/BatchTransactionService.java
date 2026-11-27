
package com.example.demo.service;

import com.example.demo.dto.request.BatchTransactionRequest;
import com.example.demo.dto.response.BatchTransactionResult;
import com.example.demo.dto.response.BatchTransactionResponse;
import com.example.demo.dto.response.TransactionResponse;

import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

@Service
public class BatchTransactionService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    BatchTransactionService.class
            );

    private static final int MAX_BATCH_SIZE = 100;

    private final AsyncTransactionService asyncService;
    private final Validator validator;

    public BatchTransactionService(
            AsyncTransactionService asyncService,
            Validator validator) {

        this.asyncService = asyncService;
        this.validator = validator;
    }

    public CompletableFuture<BatchTransactionResponse>
            processBatch(
                    List<BatchTransactionRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException(
                    "Batch cannot be empty"
            );
        }

        if (requests.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                    "Batch cannot exceed 100 transactions"
            );
        }

        if (requests.stream().anyMatch(
                request -> request == null)) {

            throw new IllegalArgumentException(
                    "Batch cannot contain null requests"
            );
        }

        List<BatchTransactionRequest> batch =
                List.copyOf(requests);

        logger.info(
                "Processing batch of {} transactions",
                batch.size()
        );

        List<CompletableFuture<BatchTransactionResult>>
                futures = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {

            futures.add(
                    processTransaction(i, batch.get(i))
            );
        }

        CompletableFuture<Void> allTransactions =
                CompletableFuture.allOf(
                        futures.toArray(
                                new CompletableFuture<?>[0]
                        )
                );

        return allTransactions.thenApply(ignored -> {

            List<BatchTransactionResult> results =
                    futures.stream()
                            .map(CompletableFuture::join)
                            .toList();

            long successful = results.stream()
                    .filter(BatchTransactionResult::success)
                    .count();

            long failed = results.size() - successful;

            logger.info(
                    "Batch completed: total={}, success={}, failed={}",
                    results.size(),
                    successful,
                    failed
            );

            return new BatchTransactionResponse(
                    results.size(),
                    successful,
                    failed,
                    results
            );
        });
    }

    private CompletableFuture<BatchTransactionResult>
            processTransaction(
                    int index,
                    BatchTransactionRequest request) {

        String type = request.transactionType();

        try {

            var violations = validator.validate(request);

            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(
                        violations
                );
            }

            CompletableFuture<TransactionResponse> future;

            if (request.deposit() != null) {

                future = asyncService.depositAsync(
                        request.deposit()
                );

            } else if (request.withdrawal() != null) {

                future = asyncService.withdrawAsync(
                        request.withdrawal()
                );

            } else {

                future = asyncService.transferAsync(
                        request.transfer()
                );
            }

            return future.handle((response, exception) -> {

                if (exception != null) {

                    logger.warn(
                            "Batch transaction failed: index={}, type={}",
                            index,
                            type,
                            exception
                    );

                    return new BatchTransactionResult(
                            index,
                            type,
                            false,
                            null,
                            "Transaction execution failed"
                    );
                }

                return new BatchTransactionResult(
                        index,
                        type,
                        true,
                        response,
                        null
                );
            });

        } catch (ConstraintViolationException exception) {

            return failedResult(
                    index,
                    type,
                    "Transaction validation failed"
            );

        } catch (RejectedExecutionException exception) {

            return failedResult(
                    index,
                    type,
                    "Transaction executor is busy"
            );

        } catch (RuntimeException exception) {

            logger.error(
                    "Unable to submit batch transaction: index={}",
                    index,
                    exception
            );

            return failedResult(
                    index,
                    type,
                    "Unable to submit transaction"
            );
        }
    }

    private CompletableFuture<BatchTransactionResult>
            failedResult(
                    int index,
                    String type,
                    String message) {

        return CompletableFuture.completedFuture(
                new BatchTransactionResult(
                        index,
                        type,
                        false,
                        null,
                        message
                )
        );
    }
}
