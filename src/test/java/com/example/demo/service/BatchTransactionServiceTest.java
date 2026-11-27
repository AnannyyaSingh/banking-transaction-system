
package com.example.demo.service;

import com.example.demo.dto.request.BatchTransactionRequest;
import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;

import com.example.demo.dto.response.BatchTransactionResponse;
import com.example.demo.dto.response.BatchTransactionResult;

import com.example.demo.entity.Account;
import com.example.demo.entity.Customer;
import com.example.demo.enums.AccountStatus;

import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.BankTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;
import com.example.demo.repository.FailedTransactionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BatchTransactionServiceTest {

    @Autowired
    private BatchTransactionService batchService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Autowired
    private TransactionAuditRepository transactionAuditRepository;

    @Autowired
    private FailedTransactionRepository failedTransactionRepository;

    private Customer customer;

    private Account sourceAccount;

    private Account destinationAccount;

    private final List<String> testKeys =
            new ArrayList<>();

    private final List<CompletableFuture<?>> pendingFutures =
            new ArrayList<>();

    // ================================================
    // TEST DATA SETUP
    // ================================================

    @BeforeEach
    void setUp() {

        testKeys.clear();
        pendingFutures.clear();

        customer = new Customer();

        customer.setFullName("Batch Test Customer");

        customer.setEmail(
                UUID.randomUUID() + "@test.example");

        customer.setPhone("9999999999");

        customer = customerRepository.saveAndFlush(customer);

        sourceAccount = createAccount("10000.00");

        destinationAccount = createAccount("5000.00");
    }

    private Account createAccount(String balance) {

        Account account = new Account();

        account.setCustomer(customer);

        account.setAccountNumber(
                "TEST" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16));

        account.setBalance(new BigDecimal(balance));

        account.setAccountStatus(
                AccountStatus.ACTIVE.name());

        return accountRepository.saveAndFlush(account);
    }

    // ================================================
    // TEST DATA CLEANUP
    // ================================================

    @AfterEach
    void cleanUp() throws Exception {

        CompletableFuture.allOf(
                pendingFutures.toArray(
                        new CompletableFuture<?>[0]))
                .handle((result, exception) -> null)
                .get(60, TimeUnit.SECONDS);

        for (String key : testKeys) {

            transactionAuditRepository.deleteAll(
                    transactionAuditRepository
                            .findByIdempotencyKey(key));

            failedTransactionRepository.deleteAll(
                    failedTransactionRepository
                            .findByIdempotencyKey(key));

            bankTransactionRepository
                    .findByIdempotencyKey(key)
                    .ifPresent(bankTransactionRepository::delete);
        }

        if (sourceAccount != null) {

            accountRepository.deleteById(
                    sourceAccount.getAccountId());
        }

        if (destinationAccount != null) {

            accountRepository.deleteById(
                    destinationAccount.getAccountId());
        }

        if (customer != null) {

            customerRepository.deleteById(
                    customer.getCustomerId());
        }
    }

    // ================================================
    // HELPER METHODS
    // ================================================

    private String generateKey() {

        String key = "BATCH-" + UUID.randomUUID();

        testKeys.add(key);

        return key;
    }

    private BigDecimal getBalance(Long accountId) {

        return accountRepository.findById(accountId)
                .orElseThrow()
                .getBalance();
    }

    private void assertBalance(
            Long accountId,
            String expectedBalance) {

        assertEquals(
                0,
                new BigDecimal(expectedBalance)
                        .compareTo(getBalance(accountId))
        );
    }

    private void assertTotalBalance(String expectedTotal) {

        BigDecimal total =
                getBalance(sourceAccount.getAccountId())
                        .add(getBalance(
                                destinationAccount.getAccountId()));

        assertEquals(
                0,
                new BigDecimal(expectedTotal)
                        .compareTo(total)
        );
    }

    private BatchTransactionRequest deposit(
            String amount,
            String key) {

        return new BatchTransactionRequest(
                new DepositRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal(amount),
                        key,
                        "Batch deposit test"
                ),
                null,
                null
        );
    }

    private BatchTransactionRequest withdrawal(
            String amount,
            String key) {

        return new BatchTransactionRequest(
                null,
                new WithdrawalRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal(amount),
                        key,
                        "Batch withdrawal test"
                ),
                null
        );
    }

    private BatchTransactionRequest transfer(
            Long fromAccountId,
            Long toAccountId,
            String amount,
            String key) {

        return new BatchTransactionRequest(
                null,
                null,
                new TransferRequest(
                        fromAccountId,
                        toAccountId,
                        new BigDecimal(amount),
                        key,
                        "Batch transfer test"
                )
        );
    }

    private BatchTransactionResponse executeBatch(
            List<BatchTransactionRequest> requests)
            throws Exception {

        CompletableFuture<BatchTransactionResponse> future =
                batchService.processBatch(requests);

        pendingFutures.add(future);

        return future.get(30, TimeUnit.SECONDS);
    }

    private void assertBatchCounts(
            BatchTransactionResponse response,
            int total,
            long successful,
            long failed) {

        assertNotNull(response);

        assertEquals(total, response.totalTransactions());

        assertEquals(
                successful,
                response.successfulTransactions());

        assertEquals(
                failed,
                response.failedTransactions());

        assertEquals(total, response.results().size());
    }

    // ================================================
    // TEST 1: SUCCESSFUL BATCH
    // ================================================

    @Test
    void shouldProcessSuccessfulBatch()
            throws Exception {

        List<BatchTransactionRequest> requests =
                List.of(
                        deposit("1000.00", generateKey()),
                        deposit("2000.00", generateKey()),
                        deposit("500.00", generateKey())
                );

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 3, 3, 0);

        for (BatchTransactionResult result
                : response.results()) {

            assertTrue(result.success());

            assertNotNull(result.transaction());

            assertNotNull(
                    result.transaction().transactionId());

            assertEquals(
                    "SUCCESS",
                    result.transaction().status());

            assertNull(result.errorMessage());
        }

        assertBalance(
                sourceAccount.getAccountId(),
                "13500.00");

        for (String key : testKeys) {

            assertTrue(
                    bankTransactionRepository
                            .findByIdempotencyKey(key)
                            .isPresent());
        }
    }

    // ================================================
    // TEST 2: MIXED TRANSACTION TYPES
    // ================================================

    @Test
    void shouldProcessMixedTransactionTypes()
            throws Exception {

        List<BatchTransactionRequest> requests =
                List.of(
                        deposit("1000.00", generateKey()),

                        withdrawal("500.00", generateKey()),

                        transfer(
                                sourceAccount.getAccountId(),
                                destinationAccount.getAccountId(),
                                "2000.00",
                                generateKey()
                        )
                );

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 3, 3, 0);

        assertEquals(
                "DEPOSIT",
                response.results().get(0).transactionType());

        assertEquals(
                "WITHDRAWAL",
                response.results().get(1).transactionType());

        assertEquals(
                "TRANSFER",
                response.results().get(2).transactionType());

        assertBalance(
                sourceAccount.getAccountId(),
                "8500.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "7000.00");

        assertTotalBalance("15500.00");
    }

    // ================================================
    // TEST 3: PARTIAL BATCH FAILURE
    // ================================================

    @Test
    void shouldPreserveSuccessfulTransactionsWhenOneFails()
            throws Exception {

        String depositKey = generateKey();

        String failedKey = generateKey();

        String transferKey = generateKey();

        List<BatchTransactionRequest> requests =
                List.of(
                        deposit("1000.00", depositKey),

                        withdrawal("50000.00", failedKey),

                        transfer(
                                sourceAccount.getAccountId(),
                                destinationAccount.getAccountId(),
                                "2000.00",
                                transferKey
                        )
                );

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 3, 2, 1);

        assertTrue(response.results().get(0).success());

        assertFalse(response.results().get(1).success());

        assertTrue(response.results().get(2).success());

        assertNull(
                response.results().get(1).transaction());

        assertEquals(
                "Transaction execution failed",
                response.results().get(1).errorMessage());

        assertBalance(
                sourceAccount.getAccountId(),
                "9000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "7000.00");

        assertTotalBalance("16000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(depositKey)
                        .isPresent());

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(transferKey)
                        .isPresent());

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(failedKey)
                        .isEmpty());

        assertEquals(
                1,
                failedTransactionRepository
                        .findByIdempotencyKey(failedKey)
                        .size());
    }

    // ================================================
    // TEST 4: EMPTY BATCH
    // ================================================

    @Test
    void shouldRejectEmptyBatch() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchService.processBatch(
                                Collections.emptyList())
                );

        assertEquals(
                "Batch cannot be empty",
                exception.getMessage());
    }

    // ================================================
    // TEST 5: NULL BATCH
    // ================================================

    @Test
    void shouldRejectNullBatch() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchService.processBatch(null)
                );

        assertEquals(
                "Batch cannot be empty",
                exception.getMessage());
    }

    // ================================================
    // TEST 6: NULL BATCH ITEM
    // ================================================

    @Test
    void shouldRejectNullBatchItem() {

        String key = generateKey();

        List<BatchTransactionRequest> requests =
                Arrays.asList(
                        deposit("100.00", key),
                        null
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchService.processBatch(requests)
                );

        assertEquals(
                "Batch cannot contain null requests",
                exception.getMessage());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());
    }

    // ================================================
    // TEST 7: MAXIMUM BATCH SIZE
    // ================================================

    @Test
    void shouldRejectBatchExceedingMaximumSize() {

        String key = generateKey();

        BatchTransactionRequest request =
                deposit("100.00", key);

        List<BatchTransactionRequest> requests =
                Collections.nCopies(101, request);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> batchService.processBatch(requests)
                );

        assertEquals(
                "Batch cannot exceed 100 transactions",
                exception.getMessage());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());
    }

    // ================================================
    // TEST 8: INVALID TRANSACTION AMOUNT
    // ================================================

    @Test
    void shouldRejectInvalidTransactionAmount()
            throws Exception {

        String invalidKey = generateKey();

        String validKey = generateKey();

        List<BatchTransactionRequest> requests =
                List.of(
                        deposit("-500.00", invalidKey),
                        deposit("1000.00", validKey)
                );

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 2, 1, 1);

        BatchTransactionResult invalidResult =
                response.results().get(0);

        assertFalse(invalidResult.success());

        assertNull(invalidResult.transaction());

        assertEquals(
                "Transaction validation failed",
                invalidResult.errorMessage());

        assertTrue(response.results().get(1).success());

        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(invalidKey)
                        .isEmpty());

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(validKey)
                        .isPresent());
    }

    // ================================================
    // TEST 9: RESPONSE ORDERING
    // ================================================

    @Test
    void shouldPreserveOriginalRequestOrder()
            throws Exception {

        List<BatchTransactionRequest> requests =
                new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            requests.add(
                    deposit("100.00", generateKey()));
        }

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 10, 10, 0);

        for (int i = 0; i < requests.size(); i++) {

            BatchTransactionResult result =
                    response.results().get(i);

            assertEquals(i, result.index());

            assertEquals("DEPOSIT", result.transactionType());

            assertTrue(result.success());

            assertNotNull(result.transaction());

            var storedTransaction =
                    bankTransactionRepository
                            .findByIdempotencyKey(
                                    testKeys.get(i))
                            .orElseThrow();

            assertEquals(
                    storedTransaction.getTransactionId(),
                    result.transaction().transactionId());
        }

        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");
    }

    // ================================================
    // TEST 10: BATCH IDEMPOTENCY
    // ================================================

    @Test
    void shouldPreventDuplicateBatchTransactions()
            throws Exception {

        String key = generateKey();

        BatchTransactionRequest request =
                deposit("1000.00", key);

        List<BatchTransactionRequest> requests =
                Collections.nCopies(5, request);

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 5, 5, 0);

        var firstTransactionId =
                response.results()
                        .get(0)
                        .transaction()
                        .transactionId();

        assertNotNull(firstTransactionId);

        for (BatchTransactionResult result
                : response.results()) {

            assertTrue(result.success());

            assertEquals(
                    firstTransactionId,
                    result.transaction().transactionId());
        }

        // Five requests but only one balance update.
        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isPresent());

        assertTrue(
                failedTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());
    }

    // ================================================
    // TEST 11: CONCURRENT BALANCE CONSERVATION
    // ================================================

    @Test
    void shouldPreserveTotalBalanceDuringConcurrentTransfers()
            throws Exception {

        List<BatchTransactionRequest> requests =
                new ArrayList<>();

        for (int i = 0; i < 5; i++) {

            requests.add(
                    transfer(
                            sourceAccount.getAccountId(),
                            destinationAccount.getAccountId(),
                            "200.00",
                            generateKey()
                    )
            );

            requests.add(
                    transfer(
                            destinationAccount.getAccountId(),
                            sourceAccount.getAccountId(),
                            "200.00",
                            generateKey()
                    )
            );
        }

        BatchTransactionResponse response =
                executeBatch(requests);

        assertBatchCounts(response, 10, 10, 0);

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "5000.00");

        assertTotalBalance("15000.00");

        for (String key : testKeys) {

            assertTrue(
                    bankTransactionRepository
                            .findByIdempotencyKey(key)
                            .isPresent());

            assertTrue(
                    failedTransactionRepository
                            .findByIdempotencyKey(key)
                            .isEmpty());
        }
    }
}
