package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;

  public @NonNull Page<Customer> getCustomers(@NonNull Pageable pageable) {
    return customerRepository.findAll(pageable);
  }

  public @NonNull Customer getById(@NonNull Long id) {
    return customerRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
  }

  @Transactional
  public @NonNull Customer create(@NonNull Customer customer) {
    customer.setTenantId(com.ims.shared.auth.TenantContext.getTenantId());
    return customerRepository.save(customer);
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
    if (updates.getGstin() != null) {
      customer.setGstin(updates.getGstin());
    }
    return customerRepository.save(customer);
  }

  @Transactional
  public void delete(@NonNull Long id) {
    Customer customer = getById(id);
    customerRepository.delete(customer);
  }

  public Map<String, Object> getCustomerLedger(@NonNull Long id) {
    Customer customer = getById(id);

    List<com.ims.model.Order> orders = orderRepository.findByCustomerId(id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices = invoiceRepository.findByCustomerId(id);
    List<com.ims.model.Payment> payments = paymentRepository.findByCustomerId(id);

    return Map.of(
        "customer", customer,
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }
}
