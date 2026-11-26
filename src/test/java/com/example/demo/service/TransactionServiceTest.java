package com.example.demo.service;

import com.example.demo.dto.request.DepositRequest;
import com.example.demo.dto.request.WithdrawalRequest;
import com.example.demo.dto.request.TransferRequest;

import com.example.demo.dto.response.TransactionResponse;

import com.example.demo.entity.Account;
import com.example.demo.entity.Customer;

import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.TransactionStatus;

import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.InactiveAccountException;
import com.example.demo.exception.InsufficientBalanceException;
import com.example.demo.exception.InvalidTransactionException;
import com.example.demo.exception.DuplicateTransactionException;

import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.BankTransactionRepository;
import com.example.demo.repository.TransactionAuditRepository;
import com.example.demo.repository.FailedTransactionRepository;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransactionServiceTest {

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

    private final List<String> testKeys = new ArrayList<>();

    // ================================================
    // TEST DATA SETUP
    // ================================================

    @BeforeEach
    void setUp() {

        testKeys.clear();

        customer = new Customer();

        customer.setFullName("Transaction Test Customer");

        customer.setEmail(
                UUID.randomUUID() + "@test.example");

        customer.setPhone("9999999999");

        customer = customerRepository.saveAndFlush(customer);

        sourceAccount = createAccount(
                new BigDecimal("10000.00"));

        destinationAccount = createAccount(
                new BigDecimal("5000.00"));
    }

    private Account createAccount(BigDecimal balance) {

        Account account = new Account();

        account.setCustomer(customer);

        account.setAccountNumber(
                "TEST" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16));

        account.setBalance(balance);

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

        String key = "TEST-" + UUID.randomUUID();

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

    private void updateAccountStatus(
            Account account,
            AccountStatus status) {

        Account existingAccount =
                accountRepository
                        .findById(account.getAccountId())
                        .orElseThrow();

        existingAccount.setAccountStatus(status.name());

        accountRepository.saveAndFlush(existingAccount);
    }

    // ================================================
    // TEST 1: SUCCESSFUL DEPOSIT
    // ================================================

    @Test
    void shouldDepositSuccessfully() {

        DepositRequest request = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("2000.00"),
                generateKey(),
                "Test deposit"
        );

        TransactionResponse response =
                transactionService.deposit(request);

        assertNotNull(response.transactionId());

        assertEquals(
                TransactionStatus.SUCCESS.name(),
                response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "12000.00");
    }

    // ================================================
    // TEST 2: SUCCESSFUL WITHDRAWAL
    // ================================================

    @Test
    void shouldWithdrawSuccessfully() {

        WithdrawalRequest request = new WithdrawalRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("3000.00"),
                generateKey(),
                "Test withdrawal"
        );

        TransactionResponse response =
                transactionService.withdraw(request);

        assertEquals(
                TransactionStatus.SUCCESS.name(),
                response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "7000.00");
    }

    // ================================================
    // TEST 3: SUCCESSFUL FUND TRANSFER
    // ================================================

    @Test
    void shouldTransferSuccessfully() {

        TransferRequest request = new TransferRequest(
                sourceAccount.getAccountId(),
                destinationAccount.getAccountId(),
                new BigDecimal("2500.00"),
                generateKey(),
                "Test transfer"
        );

        TransactionResponse response =
                transactionService.transfer(request);

        assertEquals(
                TransactionStatus.SUCCESS.name(),
                response.status());

        assertBalance(
                sourceAccount.getAccountId(),
                "7500.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "7500.00");
    }

    // ================================================
    // TEST 4: INSUFFICIENT WITHDRAWAL BALANCE
    // ================================================

    @Test
    void shouldRejectInsufficientWithdrawalBalance() {

        WithdrawalRequest request = new WithdrawalRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("15000.00"),
                generateKey(),
                "Insufficient withdrawal"
        );

        assertThrows(
                InsufficientBalanceException.class,
                () -> transactionService.withdraw(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }

    // ================================================
    // TEST 5: INSUFFICIENT TRANSFER BALANCE
    // ================================================

    @Test
    void shouldRejectInsufficientTransferBalance() {

        TransferRequest request = new TransferRequest(
                sourceAccount.getAccountId(),
                destinationAccount.getAccountId(),
                new BigDecimal("20000.00"),
                generateKey(),
                "Insufficient transfer"
        );

        assertThrows(
                InsufficientBalanceException.class,
                () -> transactionService.transfer(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "5000.00");
    }

    // ================================================
    // TEST 6: INACTIVE ACCOUNT
    // ================================================

    @Test
    void shouldRejectDepositIntoInactiveAccount() {

        updateAccountStatus(
                sourceAccount,
                AccountStatus.INACTIVE);

        DepositRequest request = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("1000.00"),
                generateKey(),
                "Inactive account deposit"
        );

        assertThrows(
                InactiveAccountException.class,
                () -> transactionService.deposit(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }

    // ================================================
    // TEST 7: BLOCKED ACCOUNT
    // ================================================

    @Test
    void shouldRejectWithdrawalFromBlockedAccount() {

        updateAccountStatus(
                sourceAccount,
                AccountStatus.BLOCKED);

        WithdrawalRequest request = new WithdrawalRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("1000.00"),
                generateKey(),
                "Blocked account withdrawal"
        );

        assertThrows(
                InactiveAccountException.class,
                () -> transactionService.withdraw(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }

    // ================================================
    // TEST 8: SAME ACCOUNT TRANSFER
    // ================================================

    @Test
    void shouldRejectSameAccountTransfer() {

        TransferRequest request = new TransferRequest(
                sourceAccount.getAccountId(),
                sourceAccount.getAccountId(),
                new BigDecimal("1000.00"),
                generateKey(),
                "Invalid transfer"
        );

        assertThrows(
                InvalidTransactionException.class,
                () -> transactionService.transfer(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }

    // ================================================
    // TEST 9: NONEXISTENT ACCOUNT
    // ================================================

    @Test
    void shouldRejectNonexistentAccount() {

        Long missingAccountId = Long.MAX_VALUE;

        assertFalse(
                accountRepository.existsById(missingAccountId));

        DepositRequest request = new DepositRequest(
                missingAccountId,
                new BigDecimal("1000.00"),
                generateKey(),
                "Nonexistent account"
        );

        assertThrows(
                AccountNotFoundException.class,
                () -> transactionService.deposit(request));
    }

    // ================================================
    // TEST 10: DUPLICATE DEPOSIT
    // ================================================

    @Test
    void shouldNotProcessDuplicateDepositTwice() {

        DepositRequest request = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("2000.00"),
                generateKey(),
                "Duplicate deposit"
        );

        TransactionResponse first =
                transactionService.deposit(request);

        TransactionResponse second =
                transactionService.deposit(request);

        assertEquals(
                first.transactionId(),
                second.transactionId());

        assertBalance(
                sourceAccount.getAccountId(),
                "12000.00");
    }

    // ================================================
    // TEST 11: DUPLICATE WITHDRAWAL
    // ================================================

    @Test
    void shouldNotProcessDuplicateWithdrawalTwice() {

        WithdrawalRequest request = new WithdrawalRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("8000.00"),
                generateKey(),
                "Duplicate withdrawal"
        );

        TransactionResponse first =
                transactionService.withdraw(request);

        TransactionResponse second =
                transactionService.withdraw(request);

        assertEquals(
                first.transactionId(),
                second.transactionId());

        assertBalance(
                sourceAccount.getAccountId(),
                "2000.00");
    }

    // ================================================
    // TEST 12: DUPLICATE TRANSFER
    // ================================================

    @Test
    void shouldNotProcessDuplicateTransferTwice() {

        TransferRequest request = new TransferRequest(
                sourceAccount.getAccountId(),
                destinationAccount.getAccountId(),
                new BigDecimal("3000.00"),
                generateKey(),
                "Duplicate transfer"
        );

        TransactionResponse first =
                transactionService.transfer(request);

        TransactionResponse second =
                transactionService.transfer(request);

        assertEquals(
                first.transactionId(),
                second.transactionId());

        assertBalance(
                sourceAccount.getAccountId(),
                "7000.00");

        assertBalance(
                destinationAccount.getAccountId(),
                "8000.00");
    }

    // ================================================
    // TEST 13: CONFLICTING IDEMPOTENCY KEY
    // ================================================

    @Test
    void shouldRejectConflictingIdempotencyKey() {

        String key = generateKey();

        DepositRequest firstRequest = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("1000.00"),
                key,
                "Deposit"
        );

        DepositRequest secondRequest = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("2000.00"),
                key,
                "Deposit"
        );

        transactionService.deposit(firstRequest);

        assertThrows(
                DuplicateTransactionException.class,
                () -> transactionService.deposit(secondRequest));

        assertBalance(
                sourceAccount.getAccountId(),
                "11000.00");
    }

    // ================================================
    // TEST 14: TRANSACTION RETRIEVAL
    // ================================================

    @Test
    void shouldRetrieveTransactionDetails() {

        String key = generateKey();

        TransactionResponse created =
                transactionService.deposit(
                        new DepositRequest(
                                sourceAccount.getAccountId(),
                                new BigDecimal("500.00"),
                                key,
                                "Retrieve transaction"
                        ));

        TransactionResponse byId =
                transactionService.getTransactionById(
                        created.transactionId());

        TransactionResponse byKey =
                transactionService
                        .getTransactionByIdempotencyKey(key);

        assertEquals(
                created.transactionId(),
                byId.transactionId());

        assertEquals(
                created.transactionId(),
                byKey.transactionId());
    }

    // ================================================
    // TEST 15: ACCOUNT TRANSACTION HISTORY
    // ================================================

    @Test
    void shouldRetrieveAccountTransactionHistory() {

        TransactionResponse deposit =
                transactionService.deposit(
                        new DepositRequest(
                                sourceAccount.getAccountId(),
                                new BigDecimal("1000.00"),
                                generateKey(),
                                "History deposit"
                        ));

        TransactionResponse withdrawal =
                transactionService.withdraw(
                        new WithdrawalRequest(
                                sourceAccount.getAccountId(),
                                new BigDecimal("500.00"),
                                generateKey(),
                                "History withdrawal"
                        ));

        TransactionResponse transfer =
                transactionService.transfer(
                        new TransferRequest(
                                sourceAccount.getAccountId(),
                                destinationAccount.getAccountId(),
                                new BigDecimal("1000.00"),
                                generateKey(),
                                "History transfer"
                        ));

        List<TransactionResponse> history =
                transactionService.getTransactionsByAccountId(
                        sourceAccount.getAccountId());

        assertEquals(3, history.size());

        List<Long> transactionIds = history.stream()
                .map(TransactionResponse::transactionId)
                .toList();

        assertTrue(
                transactionIds.contains(deposit.transactionId()));

        assertTrue(
                transactionIds.contains(withdrawal.transactionId()));

        assertTrue(
                transactionIds.contains(transfer.transactionId()));

        List<TransactionResponse> destinationHistory =
                transactionService.getTransactionsByAccountId(
                        destinationAccount.getAccountId());

        assertEquals(1, destinationHistory.size());

        assertEquals(
                transfer.transactionId(),
                destinationHistory.get(0).transactionId());
    }

    // ================================================
    // TEST 16: SUCCESSFUL TRANSACTION AUDIT
    // ================================================

    @Test
    void shouldCreateSuccessfulTransactionAuditRecords() {

        TransactionResponse response =
                transactionService.deposit(
                        new DepositRequest(
                                sourceAccount.getAccountId(),
                                new BigDecimal("1000.00"),
                                generateKey(),
                                "Audit test"
                        ));

        var audits =
                transactionService.getTransactionAuditHistory(
                        response.transactionId());

        assertEquals(3, audits.size());

        assertEquals(
                "TRANSACTION_CREATED",
                audits.get(0).eventType());

        assertEquals(
                "VALIDATION_COMPLETED",
                audits.get(1).eventType());

        assertEquals(
                "TRANSACTION_COMPLETED",
                audits.get(2).eventType());

        assertEquals(
                "SUCCESS",
                audits.get(2).newStatus());
    }

    // ================================================
    // TEST 17: PERSISTENT FAILURE LOGGING
    // ================================================

    @Test
    void shouldPersistFailureRecords() {

        String key = generateKey();

        WithdrawalRequest request = new WithdrawalRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("50000.00"),
                key,
                "Failure logging test"
        );

        assertThrows(
                InsufficientBalanceException.class,
                () -> transactionService.withdraw(request));

        var failures =
                failedTransactionRepository
                        .findByIdempotencyKey(key);

        assertEquals(1, failures.size());

        assertEquals(
                "INSUFFICIENT_BALANCE",
                failures.get(0).getFailureReason());

        var audits =
                transactionAuditRepository
                        .findByIdempotencyKey(key);

        assertEquals(1, audits.size());

        assertEquals(
                "TRANSACTION_REJECTED",
                audits.get(0).getEventType());

        assertTrue(
                bankTransactionRepository
                        .findByIdempotencyKey(key)
                        .isEmpty());

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }

    // ================================================
    // TEST 18: INVALID AMOUNT
    // ================================================

    @Test
    void shouldRejectNegativeAmount() {

        DepositRequest request = new DepositRequest(
                sourceAccount.getAccountId(),
                new BigDecimal("-500.00"),
                generateKey(),
                "Invalid amount"
        );

        assertThrows(
                ConstraintViolationException.class,
                () -> transactionService.deposit(request));

        assertBalance(
                sourceAccount.getAccountId(),
                "10000.00");
    }
}