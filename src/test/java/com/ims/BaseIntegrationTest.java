package com.ims;

import com.ims.platform.repository.TenantRepository;
import com.ims.shared.audit.AuditLogRepository;
import com.ims.tenant.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public abstract class BaseIntegrationTest {

  @Autowired protected TenantRepository tenantRepository;
  @Autowired protected UserRepository userRepository;
  @Autowired protected RoleRepository roleRepository;
  @Autowired protected CustomerRepository customerRepository;
  @Autowired protected SupplierRepository supplierRepository;
  @Autowired protected ProductRepository productRepository;
  @Autowired protected CategoryRepository categoryRepository;
  @Autowired protected OrderRepository orderRepository;
  @Autowired protected OrderItemRepository orderItemRepository;
  @Autowired protected StockMovementRepository stockMovementRepository;
  @Autowired protected InvoiceRepository invoiceRepository;
  @Autowired protected AuditLogRepository auditLogRepository;
  @Autowired protected PaymentRepository paymentRepository;
  @Autowired protected TransferOrderRepository transferOrderRepository;

  @Transactional
  protected void cleanupDatabase() {
    // Correct order to respect foreign keys
    auditLogRepository.deleteAll();
    paymentRepository.deleteAll();
    invoiceRepository.deleteAll();
    orderItemRepository.deleteAll();
    orderRepository.deleteAll();
    transferOrderRepository.deleteAll();
    stockMovementRepository.deleteAll();
    productRepository.deleteAll();
    categoryRepository.deleteAll();
    userRepository.deleteAll();
    roleRepository.deleteAll();
    customerRepository.deleteAll();
    supplierRepository.deleteAll();
    tenantRepository.deleteAll();
  }
}
