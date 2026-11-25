package com.example.demo.service.impl;

import com.example.demo.dto.request.CreateCustomerRequest;
import com.example.demo.dto.response.CustomerResponse;
import com.example.demo.entity.Customer;
import com.example.demo.exception.CustomerNotFoundException;
import com.example.demo.exception.DuplicateCustomerException;
import com.example.demo.exception.BankingException;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.service.CustomerService;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;

@Service
@Validated
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;
    private final EntityManager entityManager;

    public CustomerServiceImpl(
            CustomerRepository customerRepository,
            EntityManager entityManager) {

        this.customerRepository = customerRepository;
        this.entityManager = entityManager;
    }

    // 1. Create a new customer
    @Override
@Transactional
public CustomerResponse createCustomer(
        CreateCustomerRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateCustomerException(
                    "Customer with this email already exists");
        }

        Customer customer = new Customer();

        customer.setFullName(request.fullName().trim());
        customer.setEmail(email);
        customer.setPhone(request.phone().trim());

        Customer savedCustomer =
                customerRepository.saveAndFlush(customer);

        entityManager.refresh(savedCustomer);

        return mapToResponse(savedCustomer);
    }

    // 2. Retrieve customer by ID
    @Override
    public CustomerResponse getCustomerById(Long customerId) {

        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() ->
                        new CustomerNotFoundException(customerId));

        return mapToResponse(customer);
    }

    // 3. Retrieve customer by email
    @Override
    public CustomerResponse getCustomerByEmail(String email) {

        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        Customer customer = customerRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new BankingException(
                                "Customer not found with provided email"));

        return mapToResponse(customer);
    }

    // 4. Retrieve all customers
    @Override
    public List<CustomerResponse> getAllCustomers() {

        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Convert Customer entity into CustomerResponse DTO
    private CustomerResponse mapToResponse(Customer customer) {

        return new CustomerResponse(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getCreatedAt()
        );
    }
}