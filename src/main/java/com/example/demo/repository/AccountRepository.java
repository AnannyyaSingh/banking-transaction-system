package com.example.demo.repository;

import com.example.demo.entity.Account;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository
        extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(
            String accountNumber);

    List<Account> findByCustomer_CustomerId(
            Long customerId);

    boolean existsByAccountNumber(
            String accountNumber);

    List<Account> findByAccountStatus(
            String accountStatus);

    // Retrieve account with pessimistic write lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :accountId")
    Optional<Account> findByIdForUpdate(
            @Param("accountId") Long accountId);
}