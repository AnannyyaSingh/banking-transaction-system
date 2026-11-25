package com.example.demo.service;

import com.example.demo.dto.request.CreateCustomerRequest;
import com.example.demo.dto.response.CustomerResponse;

import jakarta.validation.Valid;
import java.util.List;

public interface CustomerService {

    CustomerResponse createCustomer(
        @Valid CreateCustomerRequest request);

    CustomerResponse getCustomerById(Long customerId);

    CustomerResponse getCustomerByEmail(String email);

    List<CustomerResponse> getAllCustomers();
}