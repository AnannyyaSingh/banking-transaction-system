package com.example.demo.service;

import com.example.demo.dto.request.CreateAccountRequest;
import com.example.demo.dto.request.CreateCustomerRequest;
import com.example.demo.dto.response.AccountResponse;
import com.example.demo.dto.response.CustomerResponse;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.CustomerNotFoundException;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private CustomerService customerService;

    // Helper method to create a test customer
    private CustomerResponse createCustomer() {

        return customerService.createCustomer(
                new CreateCustomerRequest(
                        "Account Test Customer",
                        UUID.randomUUID() + "@example.com",
                        "9876543210"
                )
        );
    }

    // Helper method to create a test account
    private AccountResponse createAccount() {

        CustomerResponse customer = createCustomer();

        return accountService.createAccount(
                new CreateAccountRequest(
                        customer.customerId())
        );
    }

    // Test 1: Account creation
    @Test
    void shouldCreateAccountSuccessfully() {

        CustomerResponse customer = createCustomer();

        AccountResponse account =
                accountService.createAccount(
                        new CreateAccountRequest(
                                customer.customerId())
                );

        assertNotNull(account.accountId());
        assertNotNull(account.accountNumber());
        assertNotNull(account.createdAt());

        assertEquals(
                customer.customerId(),
                account.customerId()
        );

        assertTrue(account.accountNumber().length() <= 20);
    }

    // Test 2: Initial balance
    @Test
    void shouldHaveZeroInitialBalance() {

        AccountResponse account = createAccount();

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(account.balance())
        );
    }

    // Test 3: Default account status
    @Test
    void shouldHaveActiveStatus() {

        AccountResponse account = createAccount();

        assertEquals("ACTIVE", account.accountStatus());
    }

    // Test 4: Unique account numbers
    @Test
    void shouldGenerateUniqueAccountNumbers() {

        AccountResponse first = createAccount();
        AccountResponse second = createAccount();

        assertNotEquals(
                first.accountNumber(),
                second.accountNumber()
        );
    }

    // Test 5: Retrieve account by ID
    @Test
    void shouldRetrieveAccountById() {

        AccountResponse created = createAccount();

        AccountResponse retrieved =
                accountService.getAccountById(
                        created.accountId());

        assertEquals(
                created.accountId(),
                retrieved.accountId()
        );
    }

    // Test 6: Retrieve account by number
    @Test
    void shouldRetrieveAccountByNumber() {

        AccountResponse created = createAccount();

        AccountResponse retrieved =
                accountService.getAccountByNumber(
                        created.accountNumber());

        assertEquals(
                created.accountId(),
                retrieved.accountId()
        );
    }

    // Test 7: Retrieve accounts by customer
    @Test
    void shouldRetrieveAccountsByCustomerId() {

        CustomerResponse customer = createCustomer();

        CreateAccountRequest request =
                new CreateAccountRequest(
                        customer.customerId());

        accountService.createAccount(request);
        accountService.createAccount(request);

        List<AccountResponse> accounts =
                accountService.getAccountsByCustomerId(
                        customer.customerId());

        assertEquals(2, accounts.size());

        assertTrue(
                accounts.stream().allMatch(
                        account -> customer.customerId()
                                .equals(account.customerId())
                )
        );
    }

    // Test 8: Retrieve account balance
    @Test
    void shouldRetrieveAccountBalance() {

        AccountResponse account = createAccount();

        BigDecimal balance =
                accountService.getAccountBalance(
                        account.accountId());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(balance)
        );
    }

    // Test 9: Invalid account ID
    @Test
    void shouldThrowExceptionForInvalidAccountId() {

        assertThrows(
                AccountNotFoundException.class,
                () -> accountService.getAccountById(
                        Long.MAX_VALUE)
        );
    }

    // Test 10: Invalid customer ID
    @Test
    void shouldRejectAccountForNonexistentCustomer() {

        CreateAccountRequest request =
                new CreateAccountRequest(Long.MAX_VALUE);

        assertThrows(
                CustomerNotFoundException.class,
                () -> accountService.createAccount(request)
        );
    }

    // Test 11: Invalid account request
    @Test
    void shouldRejectInvalidAccountRequest() {

        CreateAccountRequest request =
                new CreateAccountRequest(-1L);

        assertThrows(
                ConstraintViolationException.class,
                () -> accountService.createAccount(request)
        );
    }
}