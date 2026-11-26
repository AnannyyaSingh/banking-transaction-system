package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;
import com.example.demo.dto.response.TransactionResponse;

import com.example.demo.entity.Account;
import com.example.demo.entity.Customer;

import com.example.demo.enums.AccountStatus;

import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.exception.DuplicateTransactionException;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionConcurrencyTest {

    @Autowired
    private TransactionService transactionService;

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
            new CopyOnWriteArrayList<>();

    // ================================================
    // TEST DATA SETUP
    // ================================================

    @BeforeEach
    void setUp() {

        testKeys.clear();

        customer = new Customer();

        customer.setFullName("Concurrency Test Customer");

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
    void cleanUp() {

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

        String key = "CONCURRENT-" + UUID.randomUUID();

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
                new BigDecimal(expectedTotal).compareTo(total)
        );
    }

    // ================================================
    // CONCURRENT TASK EXECUTION HELPER
    // ================================================

    private <T> List<T> executeConcurrently(
            List<Callable<T>> tasks) throws Exception {

        int threadCount = tasks.size();

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch =
                new CountDownLatch(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<T>> futures = new ArrayList<>();

        List<T> results = new ArrayList<>();

        try {

            for (Callable<T> task : tasks) {

                futures.add(executor.submit(() -> {

                    readyLatch.countDown();

                    if (!startLatch.await(
                            30, TimeUnit.SECONDS)) {

                        throw new TimeoutException(
                                "Start signal timed out");
                    }

                    return task.call();
                }));
            }

            assertTrue(
                    readyLatch.await(30, TimeUnit.SECONDS),
                    "Threads failed to become ready");

            // Release all waiting threads
            startLatch.countDown();

            for (Future<T> future : futures) {

                results.add(
                        future.get(60, TimeUnit.SECONDS));
            }

            return results;

        } finally {

            startLatch.countDown();

            executor.shutdownNow();

            assertTrue(
                    executor.awaitTermination(
                            60, TimeUnit.SECONDS),
                    "Executor failed to terminate");
        }
    }

    // ================================================
    // TEST 1: CONCURRENT DEPOSITS
    // ================================================

    @Test
    void shouldHandleConcurrentDeposits()
            throws Exception {

        int threadCount = 10;

        List<Callable<TransactionResponse>> tasks =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            String key = generateKey();

            tasks.add(() ->
                    transactionService.deposit(
                            new DepositRequest(
                                    sourceAccount.getAccountId(),
                                    new BigDecimal("1000.00"),
                                    key,
                                    "Concurrent deposit"
                            )));
        }

        List<TransactionResponse> responses =
                executeConcurrently(tasks);

        assertEquals(10, responses.size());

        assertTrue(
                responses.stream().allMatch(
                        response ->
                                "SUCCESS".equals(response.status())));

        assertEquals(
                10L,
                responses.stream()
                        .map(TransactionResponse::transactionId)
                        .distinct()
                        .count());

        assertBalance(
                sourceAccount.getAccountId(),
                "20000.00");
    }

    // ================================================
    // TEST 2: CONCURRENT WITHDRAWALS
    // ================================================

    @Test
    void shouldHandleConcurrentWithdrawals()
            throws Exception {

        List<Callable<TransactionResponse>> tasks =
                new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            String key = generateKey();

            tasks.add(() ->
                    transactionService.withdraw(
                            new WithdrawalRequest(
                                    sourceAccount.getAccountId(),
                                    new BigDecimal("500.00"),
                                    key,
                                    "Concurrent withdrawal"
                            )));
        }

        List<TransactionResponse> responses =
                executeConcurrently(tasks);

        assertEquals(10, responses.size());

        assertTrue(
                responses.stream().allMatch(
                        response ->
                                "SUCCESS".equals(response.status())));

        assertBalance(
                sourceAccount.getAccountId(),
                "5000.00");
    }

    // ================================================
    // TEST 3: PREVENT CONCURRENT OVERDRAWING
    // ================================================

    @Test
    void shouldPreventConcurrentOverdrawing()
            throws Exception {

        AtomicInteger successful =
                new AtomicInteger(0);

        AtomicInteger rejected =
                new AtomicInteger(0);

        List<Callable<Boolean>> tasks =
                new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            String key = generateKey();

            tasks.add(() -> {

                try {

                    transactionService.withdraw(
                            new WithdrawalRequest(
                                    sourceAccount.getAccountId(),
                                    new BigDecimal("2000.00"),
                                    key,
                                    "Concurrent overdrawing test"
                            ));

                    successful.incrementAndGet();

                    return true;

                } catch (InsufficientBalanceException exception) {

                    rejected.incrementAndGet();

                    return false;
                }
            });
        }

        executeConcurrently(tasks);

        assertEquals(5, successful.get());

        assertEquals(5, rejected.get());

        assertBalance(
                sourceAccount.getAccountId(),
                "0.00");

        long failureCount = testKeys.stream()
                .mapToLong(key ->
                        failedTransactionRepository
                                .findByIdempotencyKey(key)
                                .size())
                .sum();

        assertEquals(5L, failureCount);
    }

    // ================================================
    // TEST 4: CONCURRENT FUND TRANSFERS
    // ================================================

    @Test
    void shouldHandleConcurrentTransfers()
            throws Exception {

        List<Callable<TransactionResponse>> tasks =
                new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            String key = generateKey();

            tasks.add(() ->
                    transactionService.transfer(
                            new TransferRequest(
                                    sourceAccount.getAccountId(),
                                    destinationAccount.getAccountId(),
                                    new BigDecimal("500.00"),
                                    key,
                                    "Concurrent transfer"
                            )));
        }

        List<TransactionResponse> responses =
                executeConcurrently(tasks);

        assertEquals(10, responses.size());

        assertTrue(
                responses.stream().allMatch(
                        response ->
                                "SUCCESS".equals(response.status())));

        assertBalance(
                sourceAccount.getAccountId(),
                "5000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "10000.00");

        assertTotalBalance("15000.00");
    }

    // ================================================
    // TEST 5: OPPOSITE-DIRECTION TRANSFERS
    // ================================================

    @Test
    void shouldHandleOppositeDirectionTransfers()
            throws Exception {

        List<Callable<TransactionResponse>> tasks =
                new ArrayList<>();

        for (int i = 0; i < 5; i++) {

            String forwardKey = generateKey();

            String reverseKey = generateKey();

            tasks.add(() ->
                    transactionService.transfer(
                            new TransferRequest(
                                    sourceAccount.getAccountId(),
                                    destinationAccount.getAccountId(),
                                    new BigDecimal("500.00"),
                                    forwardKey,
                                    "Forward transfer"
                            )));

            tasks.add(() ->
                    transactionService.transfer(
                            new TransferRequest(
                                    destinationAccount.getAccountId(),
                                    sourceAccount.getAccountId(),
                                    new BigDecimal("500.00"),
                                    reverseKey,
                                    "Reverse transfer"
                            )));
        }

        List<TransactionResponse> responses =
                executeConcurrently(tasks);

        assertEquals(10, responses.size());

        assertTrue(
                responses.stream().allMatch(
                        response ->
                                "SUCCESS".equals(response.status())));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "5000.00");

        assertTotalBalance("15000.00");
    }

    // ================================================
    // TEST 6: CONCURRENT IDENTICAL REQUESTS
    // ================================================

    @Test
    void shouldProcessConcurrentDuplicateRequestsOnlyOnce()
            throws Exception {

        String sharedKey = generateKey();

        WithdrawalRequest request =
                new WithdrawalRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal("8000.00"),
                        sharedKey,
                        "Concurrent duplicate withdrawal"
                );

        List<Callable<TransactionResponse>> tasks =
                new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            tasks.add(() ->
                    transactionService.withdraw(request));
        }

        List<TransactionResponse> responses =
                executeConcurrently(tasks);

        assertEquals(10, responses.size());

        Long expectedTransactionId =
                responses.get(0).transactionId();

        for (TransactionResponse response : responses) {

            assertEquals(
                    expectedTransactionId,
                    response.transactionId());

            assertEquals("SUCCESS", response.status());
        }

        assertBalance(
                sourceAccount.getAccountId(),
                "2000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(sharedKey)
                        .isPresent());

        assertEquals(
                3,
                transactionAuditRepository
                        .findByIdempotencyKey(sharedKey)
                        .size());

        assertTrue(
                failedTransactionRepository
                        .findByIdempotencyKey(sharedKey)
                        .isEmpty());
    }

    // ================================================
    // TEST 7: CONCURRENT CONFLICTING IDEMPOTENCY KEYS
    // ================================================

    @Test
    void shouldRejectConcurrentConflictingRequests()
            throws Exception {

        String sharedKey = generateKey();

        AtomicInteger successful =
                new AtomicInteger(0);

        AtomicInteger rejected =
                new AtomicInteger(0);

        DepositRequest firstRequest =
                new DepositRequest(
                        sourceAccount.getAccountId(),
                        new BigDecimal("1000.00"),
                        sharedKey,
                        "Conflicting deposit"
                );

        DepositRequest secondRequest =
                new DepositRequest(
                        destinationAccount.getAccountId(),
                        new BigDecimal("2000.00"),
                        sharedKey,
                        "Conflicting deposit"
                );

        List<Callable<Boolean>> tasks =
                new ArrayList<>();

        tasks.add(() -> {

            try {

                transactionService.deposit(firstRequest);

                successful.incrementAndGet();

                return true;

            } catch (DuplicateTransactionException exception) {

                rejected.incrementAndGet();

                return false;
            }
        });

        tasks.add(() -> {

            try {

                transactionService.deposit(secondRequest);

                successful.incrementAndGet();

                return true;

            } catch (DuplicateTransactionException exception) {

                rejected.incrementAndGet();

                return false;
            }
        });

        executeConcurrently(tasks);

        assertEquals(1, successful.get());

        assertEquals(1, rejected.get());

        var transaction =
                bankTransactionRepository
                        .findByIdempotencyKey(sharedKey)
                        .orElseThrow();

        if (transaction.getAmount()
                .compareTo(new BigDecimal("1000.00")) == 0) {

            assertBalance(
                    sourceAccount.getAccountId(),
                    "11000.00");

            assertBalance(
                    destinationAccount.getAccountId(),
                    "5000.00");

            assertTotalBalance("16000.00");

        } else {

            assertEquals(
                    0,
                    new BigDecimal("2000.00")
                            .compareTo(transaction.getAmount()));

            assertBalance(
                    sourceAccount.getAccountId(),
                    "10000.00");

            assertBalance(
                    destinationAccount.getAccountId(),
                    "7000.00");

            assertTotalBalance("17000.00");
        }

        var failures =
                failedTransactionRepository
                        .findByIdempotencyKey(sharedKey);

        assertEquals(1, failures.size());

        assertEquals(
                "DUPLICATE_REQUEST",
                failures.get(0).getFailureReason());
    }
}