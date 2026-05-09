package com.ims.tenant.service;

import com.ims.dto.CustomerHistoryResponse;
import com.ims.model.Customer;
import com.ims.model.Order;
import com.ims.order.entity.OrderStatus;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.ResourceNotFoundException;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class CustomerService {

  private final CustomerRepository customerRepository;
  private final OrderRepository orderRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;
  private final Counter customerCreatedCounter;
  private final Counter duplicateEmailCounter;
  private final Timer customerHistoryTimer;

  public CustomerService(
      CustomerRepository customerRepository,
      OrderRepository orderRepository,
      InvoiceRepository invoiceRepository,
      PaymentRepository paymentRepository,
      MeterRegistry meterRegistry) {
    this.customerRepository = customerRepository;
    this.orderRepository = orderRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentRepository = paymentRepository;
    this.customerCreatedCounter =
        Counter.builder("customer.creation.total").register(meterRegistry);
    this.duplicateEmailCounter =
        Counter.builder("customer.duplicate_email_conflicts").register(meterRegistry);
    this.customerHistoryTimer = Timer.builder("customer.history.latency").register(meterRegistry);
  }

  @Transactional(readOnly = true)
  public Page<Customer> getCustomers(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return customerRepository.findAllByTenantId(tenantId, pageable);
  }

  @Transactional(readOnly = true)
  @Cacheable(value = "customers", key = "#id")
  public Customer getById(Long id) {
    Long tenantId = TenantContext.getTenantId();
    return customerRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
  }

  @Transactional
  @CacheEvict(value = "customers", allEntries = true)
  public Customer create(Customer customer) {
    Long tenantId = TenantContext.getTenantId();
    customer.setTenantId(tenantId);

    normalizeEmail(customer);

    if (customer.getEmail() != null
        && customerRepository.existsByTenantIdAndEmailAndIsDeletedFalse(
            tenantId, customer.getEmail().toLowerCase())) {
      duplicateEmailCounter.increment();
      throw new IllegalArgumentException("Customer with this email already exists");
    }

    Customer saved = customerRepository.save(customer);
    customerCreatedCounter.increment();
    log.info(
        "Customer created: id={} tenant={} email={}", saved.getId(), tenantId, saved.getEmail());
    return saved;
  }

  @Transactional
  @CacheEvict(value = "customers", key = "#id")
  public Customer update(Long id, Customer updates) {
    Customer customer = getById(id);

    if (updates.getName() != null) {
      customer.setName(updates.getName());
    }
    if (updates.getPhone() != null) {
      customer.setPhone(updates.getPhone());
    }
    if (updates.getEmail() != null && !updates.getEmail().equalsIgnoreCase(customer.getEmail())) {
      Long tenantId = TenantContext.getTenantId();
      normalizeEmail(updates);
      if (customerRepository.existsByTenantIdAndEmailAndIsDeletedFalse(
          tenantId, updates.getEmail().toLowerCase())) {
        duplicateEmailCounter.increment();
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
  @CacheEvict(value = "customers", allEntries = true)
  public void delete(Long id) {
    Customer customer = getById(id);
    customer.setIsDeleted(true);
    customerRepository.save(customer);
    log.info("Customer soft-deleted: id={} tenant={}", id, TenantContext.getTenantId());
  }

  public Map<String, Object> getCustomerLedger(Long id) {
    Customer customer = getById(id);
    Long tenantId = TenantContext.getTenantId();

    List<Order> orders =
        orderRepository.findByTenantIdAndCustomerId(tenantId, id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices =
        invoiceRepository.findByTenantIdAndCustomerId(tenantId, id);
    List<com.ims.model.Payment> payments =
        paymentRepository.findByTenantIdAndCustomerId(tenantId, id);

    return Map.of(
        "customer", customer,
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }

  @Transactional(readOnly = true)
  public CustomerHistoryResponse getCustomerHistory(Long id) {
    return customerHistoryTimer.record(
        () -> {
          Customer customer = getById(id);
          Long tenantId = TenantContext.getTenantId();

          List<Order> orders =
              orderRepository
                  .findByTenantIdAndCustomerId(tenantId, id, Pageable.unpaged())
                  .getContent();

          int totalOrders = orders.size();
          BigDecimal totalSpent =
              orders.stream()
                  .filter(o -> o.getTotalAmount() != null)
                  .map(Order::getTotalAmount)
                  .reduce(BigDecimal.ZERO, BigDecimal::add);

          int pendingOrders =
              (int)
                  orders.stream()
                      .filter(
                          o ->
                              OrderStatus.PENDING == o.getStatus()
                                  || OrderStatus.CONFIRMED == o.getStatus())
                      .count();

          return CustomerHistoryResponse.builder()
              .customerId(customer.getId())
              .customerName(customer.getName())
              .customerEmail(customer.getEmail())
              .totalOrders(totalOrders)
              .totalSpent(totalSpent)
              .pendingOrders(pendingOrders)
              .orders(orders.stream().limit(10).toList())
              .build();
        });
  }

  private void normalizeEmail(Customer customer) {
    if (customer.getEmail() != null) {
      customer.setEmail(customer.getEmail().trim().toLowerCase());
    }
  }
}
