
package com.example.demo.repository;

import com.example.demo.entity.Customer;
import com.example.demo.entity.Account;
import com.example.demo.entity.BankTransaction;
import com.example.demo.entity.FailedTransaction;
import com.example.demo.entity.TransactionAudit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional(readOnly = true)
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


}