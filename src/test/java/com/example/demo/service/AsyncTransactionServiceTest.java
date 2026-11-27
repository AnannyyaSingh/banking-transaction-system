
package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;
import com.example.demo.dto.response.TransactionResponse;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AsyncTransactionServiceTest {

    @Autowired
    private AsyncTransactionService asyncService;

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

    @Autowired
    @Qualifier("transactionExecutor")
    private Executor transactionExecutor;

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

        customer.setFullName("Async Test Customer");

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

        // Wait for submitted operations before deleting data.
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

        String key = "ASYNC-" + UUID.randomUUID();

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

    private <T> CompletableFuture<T> track(
            CompletableFuture<T> future) {

        pendingFutures.add(future);

        return future;
    }

    private TransactionResponse await(
            CompletableFuture<TransactionResponse> future)
            throws Exception {

        return future.get(30, TimeUnit.SECONDS);
    }

    // ================================================
    // TEST 1: ASYNCHRONOUS DEPOSIT
    // ================================================

    @Test
    void shouldProcessDepositAsynchronously()
            throws Exception {

        String key = generateKey();

        DepositRequest request = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("1000.00"),
                key,
                "Async deposit test"
        );

        CompletableFuture<TransactionResponse> future =
                track(asyncService.depositAsync(request));

        TransactionResponse response = await(future);

        assertNotNull(response);
        assertNotNull(response.transactionId());

        assertEquals("SUCCESS", response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isPresent());
    }

    // ================================================
    // TEST 2: ASYNCHRONOUS WITHDRAWAL
    // ================================================

    @Test
    void shouldProcessWithdrawalAsynchronously()
            throws Exception {

        String key = generateKey();

        WithdrawalRequest request =
                new WithdrawalRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal("2000.00"),
                        key,
                        "Async withdrawal test"
                );

        CompletableFuture<TransactionResponse> future =
                track(asyncService.withdrawAsync(request));

        TransactionResponse response = await(future);

        assertNotNull(response);
        assertNotNull(response.transactionId());

        assertEquals("SUCCESS", response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "8000.00");
    }

    // ================================================
    // TEST 3: ASYNCHRONOUS TRANSFER
    // ================================================

    @Test
    void shouldProcessTransferAsynchronously()
            throws Exception {

        String key = generateKey();

        TransferRequest request =
                new TransferRequest(
                        sourceAccount.getAccountId(),
                        destinationAccount.getAccountId(),
                        new BigDecimal("2000.00"),
                        key,
                        "Async transfer test"
                );

        CompletableFuture<TransactionResponse> future =
                track(asyncService.transferAsync(request));

        TransactionResponse response = await(future);

        assertNotNull(response);
        assertNotNull(response.transactionId());

        assertEquals("SUCCESS", response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "8000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "7000.00");

        BigDecimal total =
                getBalance(sourceAccount.getAccountId())
                        .add(getBalance(
                                destinationAccount.getAccountId()));

        assertEquals(
                0,
                new BigDecimal("15000.00")
                        .compareTo(total));
    }

    // ================================================
    // TEST 4: MANAGED EXECUTOR THREAD
    // ================================================

    @Test
    void shouldExecuteOnManagedWorkerThread()
            throws Exception {

        String callerThread =
                Thread.currentThread().getName();

        CompletableFuture<String> future =
                track(CompletableFuture.supplyAsync(
                        () -> Thread.currentThread().getName(),
                        transactionExecutor
                ));

        String workerThread =
                future.get(10, TimeUnit.SECONDS);

        assertNotEquals(callerThread, workerThread);

        assertTrue(
                workerThread.startsWith("banking-txn-"),
                "Expected managed banking transaction thread"
        );
    }

    // ================================================
    // TEST 5: MULTIPLE CONCURRENT DEPOSITS
    // ================================================

    @Test
    void shouldProcessConcurrentDepositsWithoutLostUpdates()
            throws Exception {

        int numberOfDeposits = 10;

        List<CompletableFuture<TransactionResponse>>
                futures = new ArrayList<>();

        for (int i = 0; i < numberOfDeposits; i++) {

            DepositRequest request =
                    new DepositRequest(
                            sourceAccount.getAccountId(),
                            new BigDecimal("100.00"),
                            generateKey(),
                            "Concurrent async deposit " + i
                    );

            futures.add(
                    track(asyncService.depositAsync(request))
            );
        }

        CompletableFuture.allOf(
                futures.toArray(
                        new CompletableFuture<?>[0]))
                .get(30, TimeUnit.SECONDS);

        for (CompletableFuture<TransactionResponse> future
                : futures) {

            TransactionResponse response = future.join();

            assertEquals("SUCCESS", response.status());
        }

        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");

        for (String key : testKeys) {

            assertTrue(
                    bankTransactionRepository
                            .findByIdempotencyKey(key)
                            .isPresent());
        }
    }

    // ================================================
    // TEST 6: INSUFFICIENT BALANCE
    // ================================================

    @Test
    void shouldCompleteExceptionallyForInsufficientBalance() {

        String key = generateKey();

        WithdrawalRequest request =
                new WithdrawalRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal("50000.00"),
                        key,
                        "Insufficient balance test"
                );

        CompletableFuture<TransactionResponse> future =
                track(asyncService.withdrawAsync(request));

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> future.get(
                                30, TimeUnit.SECONDS)
                );

        assertNotNull(exception.getCause());

        assertTrue(future.isCompletedExceptionally());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());

        assertEquals(
                1,
                failedTransactionRepository
                        .findByIdempotencyKey(key)
                        .size());
    }

    // ================================================
    // TEST 7: CONCURRENT IDEMPOTENCY
    // ================================================

    @Test
    void shouldPreventDuplicateAsyncTransactions()
            throws Exception {

        String key = generateKey();

        DepositRequest request =
                new DepositRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal("1000.00"),
                        key,
                        "Duplicate async deposit test"
                );

        List<CompletableFuture<TransactionResponse>>
                futures = new ArrayList<>();

        for (int i = 0; i < 5; i++) {

            futures.add(
                    track(asyncService.depositAsync(request))
            );
        }

        CompletableFuture.allOf(
                futures.toArray(
                        new CompletableFuture<?>[0]))
                .get(30, TimeUnit.SECONDS);

        TransactionResponse firstResponse =
                futures.get(0).join();

        assertNotNull(firstResponse.transactionId());

        for (CompletableFuture<TransactionResponse> future
                : futures) {

            TransactionResponse response = future.join();

            assertEquals(
                    firstResponse.transactionId(),
                    response.transactionId());
        }

        // The deposit must be applied only once.
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
}
