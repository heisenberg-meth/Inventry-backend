package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    public Page<Customer> getCustomers(Pageable pageable) {
        return customerRepository.findByTenantId(TenantContext.get(), pageable);
    }

    public Customer getById(Long id) {
        return customerRepository.findByIdAndTenantId(id, TenantContext.get())
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    }

    @Transactional
    public Customer create(Customer customer) {
        customer.setTenantId(TenantContext.get());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer update(Long id, Customer updates) {
        Customer customer = getById(id);
        if (updates.getName() != null) customer.setName(updates.getName());
        if (updates.getPhone() != null) customer.setPhone(updates.getPhone());
        if (updates.getEmail() != null) customer.setEmail(updates.getEmail());
        if (updates.getAddress() != null) customer.setAddress(updates.getAddress());
        return customerRepository.save(customer);
    }

    @Transactional
    public void delete(Long id) {
        Customer customer = getById(id);
        customerRepository.delete(customer);
    }
}
