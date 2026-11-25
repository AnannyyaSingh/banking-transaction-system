package com.example.demo.service.impl;

import com.example.demo.dto.request.CreateAccountRequest;
import com.example.demo.dto.response.AccountResponse;
import com.example.demo.entity.Account;
import com.example.demo.entity.Customer;
import com.example.demo.enums.AccountStatus;
import com.example.demo.exception.AccountNotFoundException;
import com.example.demo.exception.CustomerNotFoundException;
import com.example.demo.exception.BankingException;
import com.example.demo.repository.AccountRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.service.AccountService;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            EntityManager entityManager) {

        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.entityManager = entityManager;
    }

    // 1. Create a new bank account
    @Override
    @Transactional
    public AccountResponse createAccount(
            CreateAccountRequest request) {

        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() ->
                        new CustomerNotFoundException(
                                request.customerId()));

        Account account = new Account();

        account.setCustomer(customer);
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO.setScale(2));
        account.setAccountStatus(AccountStatus.ACTIVE.name());

        Account savedAccount =
                accountRepository.saveAndFlush(account);

        entityManager.refresh(savedAccount);

        return mapToResponse(savedAccount);
    }

    // 2. Retrieve account by ID
    @Override
    public AccountResponse getAccountById(Long accountId) {

        Account account = findAccount(accountId);

        return mapToResponse(account);
    }

    // 3. Retrieve account by account number
    @Override
    public AccountResponse getAccountByNumber(
            String accountNumber) {

        Account account = accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new BankingException(
                                "Account not found with number: "
                                        + accountNumber));

        return mapToResponse(account);
    }

    // 4. Retrieve accounts belonging to a customer
    @Override
    public List<AccountResponse> getAccountsByCustomerId(
            Long customerId) {

        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        return accountRepository
                .findByCustomer_CustomerId(customerId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // 5. Retrieve all accounts
    @Override
    public List<AccountResponse> getAllAccounts() {

        return accountRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // 6. Retrieve account balance
    @Override
    public BigDecimal getAccountBalance(Long accountId) {

        return findAccount(accountId).getBalance();
    }

    // Find an account or throw an exception
    private Account findAccount(Long accountId) {

        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new AccountNotFoundException(accountId));
    }

    // Generate a unique account number candidate
    private String generateAccountNumber() {

        String accountNumber;

        do {
            accountNumber = "ACC" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();

        } while (accountRepository
                .existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    // Convert Account entity into AccountResponse DTO
    private AccountResponse mapToResponse(Account account) {

        return new AccountResponse(
                account.getAccountId(),
                account.getCustomer().getCustomerId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getAccountStatus(),
                account.getCreatedAt()
        );
    }
}