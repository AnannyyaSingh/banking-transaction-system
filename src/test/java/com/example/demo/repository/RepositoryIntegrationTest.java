
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
}