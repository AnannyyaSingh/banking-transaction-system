
package com.example.demo.repository;

import com.example.demo.entity.Customer;
import com.example.demo.entity.Account;
import com.example.demo.entity.BankTransaction;
import com.example.demo.entity.FailedTransaction;
import com.example.demo.entity.TransactionAudit;

import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class RepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private BankTransactionRepository bankTransactionRepository;

    @Autowired
    private FailedTransactionRepository failedTransactionRepository;

    @Autowired
    private TransactionAuditRepository transactionAuditRepository;

    private BankTransaction createTestTransaction(
        String type,
        Account sourceAccount,
        Account destinationAccount,
        BigDecimal amount) {

    BankTransaction transaction = new BankTransaction();

    transaction.setIdempotencyKey(
            "TEST-" + UUID.randomUUID());

    transaction.setTransactionType(type);

    transaction.setSourceAccount(sourceAccount);

    transaction.setDestinationAccount(destinationAccount);

    transaction.setAmount(amount);

    transaction.setStatus("PENDING");

    transaction.setDescription(
            "Temporary repository integration test");

    return bankTransactionRepository.saveAndFlush(transaction);
}

@BeforeEach
void setUpTestData() {

    List<Account> accounts = accountRepository.findAll();

    assertTrue(accounts.size() >= 2,
            "At least two sample accounts are required");

    Account account1 = accounts.get(0);
    Account account2 = accounts.get(1);

    // Sample deposit
    createTestTransaction(
            "DEPOSIT",
            null,
            account1,
            new BigDecimal("500.00"));

    // Sample withdrawal
    createTestTransaction(
            "WITHDRAWAL",
            account1,
            null,
            new BigDecimal("200.00"));

    // Sample transfer
    createTestTransaction(
            "TRANSFER",
            account1,
            account2,
            new BigDecimal("1000.00"));

    System.out.println(
            "Temporary transaction test data created");
}

    @Test
    void testCustomerRepository() {

        List<Customer> customers =
                customerRepository.findAll();

        assertFalse(customers.isEmpty());
        assertEquals(customerRepository.count(),
                customers.size());

        System.out.println("\n===== CUSTOMERS =====");
        System.out.println("Total customers: "
                + customers.size());

        for (Customer customer : customers) {

            System.out.println(
                    "Customer ID: "
                    + customer.getCustomerId()
            );
        }
    }

    @Test
    void testAccountRepository() {

        List<Account> accounts =
                accountRepository.findAll();

        assertFalse(accounts.isEmpty());
        assertEquals(accountRepository.count(),
                accounts.size());

        System.out.println("\n===== ACCOUNTS =====");
        System.out.println("Total accounts: "
                + accounts.size());

        for (Account account : accounts) {

            System.out.println(
                    "Account ID: "
                    + account.getAccountId()
            );
        }
    }

    @Test
    void testBankTransactionRepository() {

        List<BankTransaction> transactions =
                bankTransactionRepository.findAll();

        assertEquals(bankTransactionRepository.count(),
                transactions.size());

        System.out.println("\n===== TRANSACTIONS =====");
        System.out.println("Total transactions: "
                + transactions.size());

        for (BankTransaction transaction : transactions) {

            System.out.println(
                    "Transaction ID: "
                    + transaction.getTransactionId()
                    + " | Type: "
                    + transaction.getTransactionType()
                    + " | Amount: "
                    + transaction.getAmount()
                    + " | Status: "
                    + transaction.getStatus()
            );
        }
    }

    @Test
    void testFailedTransactionRepository() {

        List<FailedTransaction> failures =
                failedTransactionRepository.findAll();

        assertEquals(failedTransactionRepository.count(),
                failures.size());

        System.out.println("\n===== FAILED TRANSACTIONS =====");
        System.out.println("Total failed transactions: "
                + failures.size());
    }

    @Test
    void testTransactionAuditRepository() {

        List<TransactionAudit> audits =
                transactionAuditRepository.findAll();

        assertEquals(transactionAuditRepository.count(),
                audits.size());

        System.out.println("\n===== TRANSACTION AUDITS =====");
        System.out.println("Total audit records: "
                + audits.size());
    }
    @Test
    void testFindCustomerByEmail() {
        Customer existingCustomer =
        customerRepository.findAll().get(0);
        
        Customer foundCustomer = customerRepository.findByEmail(existingCustomer.getEmail()).orElseThrow();
        assertEquals(existingCustomer.getCustomerId(),
        foundCustomer.getCustomerId());
        System.out.println("Find customer by email: PASSED");
}

@Test
void testExistsByEmail() {

    Customer existingCustomer =
            customerRepository.findAll().get(0);

    assertTrue(customerRepository.existsByEmail(
            existingCustomer.getEmail()));

    System.out.println("Customer existence check: PASSED");
}

@Test
void testFindNonExistingCustomer() {

    assertTrue(customerRepository
            .findByEmail("nonexistent@example.invalid")
            .isEmpty());

    System.out.println("Nonexistent customer check: PASSED");
}

@Test
void testFindByAccountNumber() {

    Account existingAccount =
            accountRepository.findAll().get(0);

    Account foundAccount = accountRepository
            .findByAccountNumber(
                    existingAccount.getAccountNumber())
            .orElseThrow();

    assertEquals(existingAccount.getAccountId(),
            foundAccount.getAccountId());

    System.out.println("Find account by number: PASSED");
}

@Test
void testExistsByAccountNumber() {

    Account existingAccount =
            accountRepository.findAll().get(0);

    assertTrue(accountRepository.existsByAccountNumber(
            existingAccount.getAccountNumber()));

    System.out.println("Account existence check: PASSED");
}

@Test
void testFindAccountsByCustomerId() {

    Account existingAccount =
            accountRepository.findAll().get(0);

    Long customerId =
            existingAccount.getCustomer().getCustomerId();

    List<Account> accounts =
            accountRepository.findByCustomer_CustomerId(
                    customerId);

    assertFalse(accounts.isEmpty());

    for (Account account : accounts) {

        assertEquals(customerId,
                account.getCustomer().getCustomerId());
    }

    assertTrue(accounts.stream().anyMatch(account ->
            account.getAccountId().equals(
                    existingAccount.getAccountId())));

    System.out.println("Customer account relationship: PASSED");
}

@Test
void testFindByAccountStatus() {

    Account existingAccount =
            accountRepository.findAll().get(0);

    String status = existingAccount.getAccountStatus();

    List<Account> accounts =
            accountRepository.findByAccountStatus(status);

    assertFalse(accounts.isEmpty());

    for (Account account : accounts) {

        assertEquals(status, account.getAccountStatus());
    }

    System.out.println("Find accounts by status: PASSED");
}

@Test
void testFindByIdempotencyKey() {

    List<BankTransaction> transactions =
            bankTransactionRepository.findAll();

    assertFalse(transactions.isEmpty(),
            "Sample transactions are required");

    BankTransaction existingTransaction =
            transactions.get(0);

    BankTransaction foundTransaction =
            bankTransactionRepository
                    .findByIdempotencyKey(
                            existingTransaction.getIdempotencyKey())
                    .orElseThrow();

    assertEquals(
            existingTransaction.getTransactionId(),
            foundTransaction.getTransactionId());

    System.out.println("Find by idempotency key: PASSED");
}

@Test
void testExistsByIdempotencyKey() {

    BankTransaction transaction =
            bankTransactionRepository.findAll().get(0);

    assertTrue(
            bankTransactionRepository.existsByIdempotencyKey(
                    transaction.getIdempotencyKey()));

    System.out.println("Idempotency existence check: PASSED");
}

@Test
void testNonExistingIdempotencyKey() {

    String key = java.util.UUID.randomUUID().toString();

    assertFalse(
            bankTransactionRepository.existsByIdempotencyKey(key));

    assertTrue(
            bankTransactionRepository
                    .findByIdempotencyKey(key)
                    .isEmpty());

    System.out.println("Nonexistent idempotency key: PASSED");
}

@Test
void testFindTransactionsByStatus() {

    BankTransaction existingTransaction =
            bankTransactionRepository.findAll().get(0);

    String status = existingTransaction.getStatus();

    List<BankTransaction> transactions =
            bankTransactionRepository.findByStatus(status);

    assertFalse(transactions.isEmpty());

    for (BankTransaction transaction : transactions) {

        assertEquals(status, transaction.getStatus());
    }

    System.out.println("Find transactions by status: PASSED");
}

@Test
void testFindByTransactionType() {

    BankTransaction existingTransaction =
            bankTransactionRepository.findAll().get(0);

    String type = existingTransaction.getTransactionType();

    List<BankTransaction> transactions =
            bankTransactionRepository.findByTransactionType(type);

    assertFalse(transactions.isEmpty());

    for (BankTransaction transaction : transactions) {

        assertEquals(type, transaction.getTransactionType());
    }

    System.out.println("Find transactions by type: PASSED");
}

@Test
void testFindBySourceAccount() {

    List<BankTransaction> allTransactions =
            bankTransactionRepository.findAll();

    for (Account account : accountRepository.findAll()) {

        Long accountId = account.getAccountId();

        long expectedCount = allTransactions.stream()
                .filter(t -> t.getSourceAccount() != null)
                .filter(t -> accountId.equals(
                        t.getSourceAccount().getAccountId()))
                .count();

        List<BankTransaction> transactions =
                bankTransactionRepository
                        .findBySourceAccount_AccountId(accountId);

        assertEquals(expectedCount, transactions.size());

        for (BankTransaction transaction : transactions) {

            assertEquals(accountId,
                    transaction.getSourceAccount().getAccountId());
        }
    }

    System.out.println("Source account query: PASSED");
}

@Test
void testFindByDestinationAccount() {

    List<BankTransaction> allTransactions =
            bankTransactionRepository.findAll();

    for (Account account : accountRepository.findAll()) {

        Long accountId = account.getAccountId();

        long expectedCount = allTransactions.stream()
                .filter(t -> t.getDestinationAccount() != null)
                .filter(t -> accountId.equals(
                        t.getDestinationAccount().getAccountId()))
                .count();

        List<BankTransaction> transactions =
                bankTransactionRepository
                        .findByDestinationAccount_AccountId(accountId);

        assertEquals(expectedCount, transactions.size());

        for (BankTransaction transaction : transactions) {

            assertEquals(accountId,
                    transaction.getDestinationAccount().getAccountId());
        }
    }

    System.out.println("Destination account query: PASSED");
}

@Test
void testFindByFailureReason() {

    List<FailedTransaction> failures =
            failedTransactionRepository.findAll();

    for (FailedTransaction failure : failures) {

        String reason = failure.getFailureReason();

        List<FailedTransaction> matchingFailures =
                failedTransactionRepository
                        .findByFailureReason(reason);

        assertFalse(matchingFailures.isEmpty());

        for (FailedTransaction matching : matchingFailures) {

            assertEquals(reason, matching.getFailureReason());
        }
    }

    assertTrue(failedTransactionRepository
            .findByFailureReason("NON_EXISTENT_TEST_REASON")
            .isEmpty());

    System.out.println("Failure reason query: PASSED");
}

@Test
void testFindFailuresByTransactionId() {

    List<FailedTransaction> allFailures =
            failedTransactionRepository.findAll();

    for (BankTransaction transaction :
            bankTransactionRepository.findAll()) {

        Long transactionId = transaction.getTransactionId();

        long expectedCount = allFailures.stream()
                .filter(f -> f.getTransaction() != null)
                .filter(f -> transactionId.equals(
                        f.getTransaction().getTransactionId()))
                .count();

        List<FailedTransaction> failures =
                failedTransactionRepository
                        .findByTransaction_TransactionId(transactionId);

        assertEquals(expectedCount, failures.size());

        for (FailedTransaction failure : failures) {

            assertEquals(transactionId,
                    failure.getTransaction().getTransactionId());
        }
    }

    System.out.println("Failed transaction relationship: PASSED");
}

@Test
void testFindFailuresByIdempotencyKey() {

    String key = java.util.UUID.randomUUID().toString();

    assertTrue(failedTransactionRepository
            .findByIdempotencyKey(key)
            .isEmpty());

    System.out.println("Failure idempotency lookup: PASSED");
}

@Test
void testFindAuditsByTransactionId() {

    List<TransactionAudit> allAudits =
            transactionAuditRepository.findAll();

    for (BankTransaction transaction :
            bankTransactionRepository.findAll()) {

        Long transactionId = transaction.getTransactionId();

        long expectedCount = allAudits.stream()
                .filter(a -> a.getTransaction() != null)
                .filter(a -> transactionId.equals(
                        a.getTransaction().getTransactionId()))
                .count();

        List<TransactionAudit> audits =
                transactionAuditRepository
                        .findByTransaction_TransactionIdOrderByCreatedAtAscAuditIdAsc(
                                transactionId);

        assertEquals(expectedCount, audits.size());

        for (TransactionAudit audit : audits) {

            assertEquals(transactionId,
                    audit.getTransaction().getTransactionId());
        }
    }

    System.out.println("Transaction audit relationship: PASSED");
}

@Test
void testFindByEventType() {

    List<TransactionAudit> audits =
            transactionAuditRepository.findAll();

    for (TransactionAudit audit : audits) {

        String eventType = audit.getEventType();

        List<TransactionAudit> matchingAudits =
                transactionAuditRepository.findByEventType(eventType);

        assertFalse(matchingAudits.isEmpty());

        for (TransactionAudit matching : matchingAudits) {

            assertEquals(eventType, matching.getEventType());
        }
    }

    assertTrue(transactionAuditRepository
            .findByEventType("NON_EXISTENT_TEST_EVENT")
            .isEmpty());

    System.out.println("Audit event type query: PASSED");
}

@Test
void testFindAuditsByIdempotencyKey() {

    String key = java.util.UUID.randomUUID().toString();

    assertTrue(transactionAuditRepository
            .findByIdempotencyKey(key)
            .isEmpty());

    System.out.println("Audit idempotency lookup: PASSED");
}

}