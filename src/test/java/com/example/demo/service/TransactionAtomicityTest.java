
package com.example.demo.service;

import com.example.demo.dto.request.TransferRequest;
import com.example.demo.dto.response.TransactionResponse;

import com.example.demo.entity.Account;
import com.example.demo.entity.Customer;
import com.example.demo.entity.TransactionAudit;

import com.example.demo.enums.AccountStatus;

import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.BankTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;
import com.example.demo.repository.FailedTransactionRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import org.springframework.dao.PessimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TransactionAtomicityTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @MockitoSpyBean
    private TransactionAuditRepository transactionAuditRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FailedTransactionRepository failedTransactionRepository;

    private Customer customer;

    private Account sourceAccount;

    private Account destinationAccount;

    private final List<String> testKeys = new ArrayList<>();

    // ================================================
    // TEST DATA SETUP
    // ================================================

    @BeforeEach
    void setUp() {

        testKeys.clear();

        customer = new Customer();

        customer.setFullName("Atomicity Test Customer");

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

        reset(transactionAuditRepository);

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

        String key = "ATOMICITY-" + UUID.randomUUID();

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

    private TransferRequest createTransferRequest(String key) {

        return new TransferRequest(
                sourceAccount.getAccountId(),
                destinationAccount.getAccountId(),
                new BigDecimal("2000.00"),
                key,
                "Atomicity test transfer"
        );
    }

    // ================================================
    // TEST 1: ROLLBACK AFTER BALANCE MODIFICATION
    // ================================================

    @Test
    void shouldRollbackTransferAfterCompletionFailure() {

        String key = generateKey();

        AtomicInteger completionAttempts =
                new AtomicInteger();

        doAnswer(invocation -> {

            TransactionAudit audit =
                    invocation.getArgument(0);

            if (key.equals(audit.getIdempotencyKey())
                    && "TRANSACTION_COMPLETED"
                            .equals(audit.getEventType())) {

                completionAttempts.incrementAndGet();

                throw new IllegalStateException(
                        "Simulated transaction completion failure"
                );
            }

            // Persist the audit within the active transaction
            entityManager.persist(audit);

            return audit;

        }).when(transactionAuditRepository)
                .save(any(TransactionAudit.class));

        assertThrows(
                IllegalStateException.class,
                () -> transactionService.transfer(
                        createTransferRequest(key))
        );

        assertEquals(1, completionAttempts.get());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "5000.00");

        assertTotalBalance("15000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());

        assertEquals(
                1,
                failedTransactionRepository
                        .findByIdempotencyKey(key)
                        .size());

        var audits =
                transactionAuditRepository
                        .findByIdempotencyKey(key);

        assertEquals(1, audits.size());

        assertEquals(
                "TRANSACTION_FAILED",
                audits.get(0).getEventType());
    }

    // ================================================
    // TEST 2: SUCCESSFUL RETRY AFTER LOCKING FAILURE
    // ================================================

    @Test
    void shouldRetryAndUpdateBalancesExactlyOnce() {

        String key = generateKey();

        AtomicInteger completionAttempts =
                new AtomicInteger();

        doAnswer(invocation -> {

            TransactionAudit audit =
                    invocation.getArgument(0);

            if (key.equals(audit.getIdempotencyKey())
                    && "TRANSACTION_COMPLETED"
                            .equals(audit.getEventType())) {

                if (completionAttempts.incrementAndGet() == 1) {

                    throw new PessimisticLockingFailureException(
                            "Simulated temporary locking failure"
                    );
                }
            }

            // Persist the audit within the active transaction
            entityManager.persist(audit);

            return audit;

        }).when(transactionAuditRepository)
                .save(any(TransactionAudit.class));

        TransactionResponse response =
                transactionService.transfer(
                        createTransferRequest(key));

        assertNotNull(response.transactionId());

        assertEquals("SUCCESS", response.status());

        assertEquals(2, completionAttempts.get());

        assertBalance(
                sourceAccount.getAccountId(),
                "8000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "7000.00");

        assertTotalBalance("15000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isPresent());

        assertTrue(
                failedTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());

        var audits =
                transactionAuditRepository
                        .findByIdempotencyKey(key);

        assertEquals(3, audits.size());

        assertEquals(
                1L,
                audits.stream()
                        .filter(audit ->
                                "TRANSACTION_COMPLETED"
                                        .equals(audit.getEventType()))
                        .count());
    }

    // ================================================
    // TEST 3: ROLLBACK AFTER EXHAUSTED RETRIES
    // ================================================

    @Test
    void shouldRollbackAfterMaximumRetryAttempts() {

        String key = generateKey();

        AtomicInteger completionAttempts =
                new AtomicInteger();

        doAnswer(invocation -> {

            TransactionAudit audit =
                    invocation.getArgument(0);

            if (key.equals(audit.getIdempotencyKey())
                    && "TRANSACTION_COMPLETED"
                            .equals(audit.getEventType())) {

                completionAttempts.incrementAndGet();

                throw new PessimisticLockingFailureException(
                        "Simulated persistent locking failure"
                );
            }

            // Persist the audit within the active transaction
            entityManager.persist(audit);

            return audit;

        }).when(transactionAuditRepository)
                .save(any(TransactionAudit.class));

        assertThrows(
                PessimisticLockingFailureException.class,
                () -> transactionService.transfer(
                        createTransferRequest(key))
        );

        assertEquals(3, completionAttempts.get());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "5000.00");

        assertTotalBalance("15000.00");

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());

        var failures =
                failedTransactionRepository
                        .findByIdempotencyKey(key);

        assertEquals(1, failures.size());

        assertEquals(
                "CONCURRENCY_CONFLICT",
                failures.get(0).getFailureReason());

        var audits =
                transactionAuditRepository
                        .findByIdempotencyKey(key);

        assertEquals(1, audits.size());

        assertEquals(
                "TRANSACTION_FAILED",
                audits.get(0).getEventType());
    }
}
