package com.example.demo.service;

import com.example.demo.dto.request.CreateCustomerRequest;
import com.example.demo.dto.response.CustomerResponse;
import com.example.demo.exception.CustomerNotFoundException;
import com.example.demo.exception.DuplicateCustomerException;

import jakarta.validation.ConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    private CreateCustomerRequest createRequest() {

        return new CreateCustomerRequest(
                "Test Customer",
                UUID.randomUUID() + "@example.com",
                "9876543210"
        );
    }

    // Test 1: Customer creation
    @Test
    void shouldCreateCustomerSuccessfully() {

        CreateCustomerRequest request = createRequest();

        CustomerResponse response =
                customerService.createCustomer(request);

        assertNotNull(response.customerId());
        assertEquals(request.fullName(), response.fullName());
        assertEquals(request.email(), response.email());
        assertEquals(request.phone(), response.phone());
        assertNotNull(response.createdAt());
    }

    // Test 2: Duplicate customer
    @Test
    void shouldRejectDuplicateEmail() {

        CreateCustomerRequest request = createRequest();

        customerService.createCustomer(request);

        assertThrows(
                DuplicateCustomerException.class,
                () -> customerService.createCustomer(request)
        );
    }

    // Test 3: Email normalization
    @Test
    void shouldNormalizeEmail() {

        String email = UUID.randomUUID()
                + "@EXAMPLE.COM";

        CreateCustomerRequest request =
                new CreateCustomerRequest(
                        "Test Customer",
                        email,
                        "9876543210"
                );

        CustomerResponse response =
                customerService.createCustomer(request);

        assertEquals(
                email.toLowerCase(java.util.Locale.ROOT),
                response.email()
        );
    }

    // Test 4: Retrieve customer by ID
    @Test
    void shouldRetrieveCustomerById() {

        CustomerResponse created =
                customerService.createCustomer(createRequest());

        CustomerResponse retrieved =
                customerService.getCustomerById(
                        created.customerId());

        assertEquals(
                created.customerId(),
                retrieved.customerId()
        );

        assertEquals(created.email(), retrieved.email());
    }

    // Test 5: Retrieve customer by email
    @Test
    void shouldRetrieveCustomerByEmail() {

        CustomerResponse created =
                customerService.createCustomer(createRequest());

        CustomerResponse retrieved =
                customerService.getCustomerByEmail(
                        created.email());

        assertEquals(
                created.customerId(),
                retrieved.customerId()
        );
    }

    // Test 6: Invalid customer ID
    @Test
    void shouldThrowExceptionForInvalidCustomerId() {

        assertThrows(
                CustomerNotFoundException.class,
                () -> customerService.getCustomerById(
                        Long.MAX_VALUE)
        );
    }

    // Test 7: Invalid request validation
    @Test
    void shouldRejectInvalidCustomerRequest() {

        CreateCustomerRequest request =
                new CreateCustomerRequest(
                        "",
                        "invalid-email",
                        ""
                );

        assertThrows(
                ConstraintViolationException.class,
                () -> customerService.createCustomer(request)
        );
    }
}