package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.ResourceNotFoundException;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;

  public Page<Customer> getCustomers(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return customerRepository.findAllByTenantId(tenantId, pageable);
  }

  public Customer getById(Long id) {
    Long tenantId = TenantContext.getTenantId();
    return customerRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
  }

  @Transactional
  public Customer create(Customer customer) {
    Long tenantId = TenantContext.getTenantId();
    customer.setTenantId(tenantId);

    if (customer.getEmail() != null
        && customerRepository.existsByTenantIdAndEmailAndIsDeletedFalse(tenantId, customer.getEmail())) {
      throw new IllegalArgumentException("Customer with this email already exists");
    }

    return customerRepository.save(customer);
  }

  @Transactional
  public Customer update(Long id, Customer updates) {
    Customer customer = getById(id);

    if (updates.getName() != null) {
      customer.setName(updates.getName());
    }
    if (updates.getPhone() != null) {
      customer.setPhone(updates.getPhone());
    }
    if (updates.getEmail() != null && !updates.getEmail().equals(customer.getEmail())) {
      Long tenantId = TenantContext.getTenantId();
      if (customerRepository.existsByTenantIdAndEmailAndIsDeletedFalse(tenantId, updates.getEmail())) {
        throw new IllegalArgumentException("Customer with this email already exists");
      }
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
  public void delete(Long id) {
    Customer customer = getById(id);
    customer.setIsDeleted(true);
    customerRepository.save(customer);
  }

  public Map<String, Object> getCustomerLedger(Long id) {
    Customer customer = getById(id);
    Long tenantId = TenantContext.getTenantId();

    List<com.ims.model.Order> orders = orderRepository.findByTenantIdAndCustomerId(tenantId, id, Pageable.unpaged())
        .getContent();
    List<com.ims.model.Invoice> invoices = invoiceRepository.findByTenantIdAndCustomerId(tenantId, id);
    List<com.ims.model.Payment> payments = paymentRepository.findByTenantIdAndCustomerId(tenantId, id);

    return Map.of(
        "customer", customer,
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }
}
