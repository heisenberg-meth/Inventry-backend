package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.tenant.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository customerRepository;

  public @NonNull Page<Customer> getCustomers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(customerRepository.findAll(pageable));
  }

  public @NonNull Customer getById(@NonNull Long id) {
    return Objects.requireNonNull(customerRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Customer not found")));
  }

  @Transactional
  public @NonNull Customer create(@NonNull Customer customer) {
    return Objects.requireNonNull(customerRepository.save(customer));
  }

  @Transactional
  public @NonNull Customer update(@NonNull Long id, @NonNull Customer updates) {
    Customer customer = getById(id);
    if (updates.getName() != null) {
      customer.setName(updates.getName());
    }
    if (updates.getPhone() != null) {
      customer.setPhone(updates.getPhone());
    }
    if (updates.getEmail() != null) {
      customer.setEmail(updates.getEmail());
    }
    if (updates.getAddress() != null) {
      customer.setAddress(updates.getAddress());
    }
    return Objects.requireNonNull(customerRepository.save(customer));
  }

  @Transactional
  public void delete(@NonNull Long id) {
    Customer customer = getById(id);
    customerRepository.delete(customer);
  }
}
