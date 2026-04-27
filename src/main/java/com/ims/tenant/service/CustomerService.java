package com.ims.tenant.service;

import com.ims.dto.request.CustomerRequest;
import com.ims.dto.response.CustomerResponse;
import com.ims.model.Customer;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.Payment;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  public Page<CustomerResponse> getCustomers(Pageable pageable) {
    com.ims.shared.auth.TenantContext.assertTenantPresent();
    return customerRepository.findAll(pageable).map(this::toResponse);
  }

  public Customer getById(Long id) {
    com.ims.shared.auth.TenantContext.assertTenantPresent();
    return Objects.requireNonNull(
        customerRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Customer not found")));
  }

  public CustomerResponse getCustomerResponseById(Long id) {
    return toResponse(getById(id));
  }

  @Transactional
  public CustomerResponse create(CustomerRequest request) {
    Long tenantId = com.ims.shared.auth.TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context missing");
    }

    Customer customer = new Customer();
    customer.setName(request.getName());
    customer.setPhone(request.getPhone());
    customer.setEmail(request.getEmail());
    customer.setAddress(request.getAddress());
    customer.setGstin(request.getGstin());

    Customer saved = customerRepository.save(customer);
    return toResponse(saved);
  }

  @Transactional
  public CustomerResponse update(Long id, CustomerRequest updates) {
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
    Customer updated = customerRepository.save(customer);
    return toResponse(updated);
  }

  @Transactional
  public void delete(Long id) {
    Customer customer = getById(id);
    customerRepository.delete(customer);
  }

  public Map<String, Object> getCustomerLedger(Long id) {
    Long tenantId = com.ims.shared.auth.TenantContext.requireTenantId();
    Customer customer = getById(id);

    List<Order> orders = orderRepository.findByCustomerId(id, Pageable.unpaged()).getContent();
    List<Invoice> invoices = invoiceRepository.findByCustomerId(id, tenantId);
    List<Payment> payments = paymentRepository.findByCustomerId(id, tenantId);

    return Objects.requireNonNull(
        Map.of(
            "customer", toResponse(customer),
            "orders", orders,
            "invoices", invoices,
            "payments", payments));
  }

  private CustomerResponse toResponse(Customer customer) {
    return Objects.requireNonNull(
        CustomerResponse.builder()
            .id(customer.getId())
            .name(customer.getName())
            .phone(customer.getPhone())
            .email(customer.getEmail())
            .address(customer.getAddress())
            .gstin(customer.getGstin())
            .build());
  }
}
