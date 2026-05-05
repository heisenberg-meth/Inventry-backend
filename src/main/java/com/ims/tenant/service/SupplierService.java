package com.ims.tenant.service;

import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.model.Supplier;
import com.ims.tenant.repository.SupplierRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

  private final SupplierRepository supplierRepository;
  private final OrderRepository orderRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  public Page<Supplier> getSuppliers(Pageable pageable) {
    return Objects.requireNonNull(supplierRepository.findAll(pageable));
  }

  public Supplier getById(Long id) {
    return Objects.requireNonNull(supplierRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found")));
  }

  @Transactional
  public Supplier create(Supplier supplier) {
    supplier.setTenantId(com.ims.shared.auth.TenantContext.getTenantId());
    Supplier savedSupplier = Objects.requireNonNull(supplierRepository.save(supplier));

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.SUPPLIER,
        savedSupplier.getId(),
        "Created supplier: " + savedSupplier.getName());

    return savedSupplier;
  }

  @Transactional
  public Supplier update(Long id, Supplier updates) {
    Supplier supplier = getById(id);
    if (updates.getName() != null) {
      supplier.setName(updates.getName());
    }
    if (updates.getPhone() != null) {
      supplier.setPhone(updates.getPhone());
    }
    if (updates.getEmail() != null) {
      supplier.setEmail(updates.getEmail());
    }
    if (updates.getAddress() != null) {
      supplier.setAddress(updates.getAddress());
    }
    if (updates.getGstin() != null) {
      supplier.setGstin(updates.getGstin());
    }
    Supplier updatedSupplier = Objects.requireNonNull(supplierRepository.save(supplier));

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.SUPPLIER,
        updatedSupplier.getId(),
        "Updated supplier: " + updatedSupplier.getName());

    return updatedSupplier;
  }

  @Transactional
  @PreAuthorize("hasAuthority('delete_supplier')")
  public void delete(Long id) {
    Supplier supplier = getById(id);
    supplierRepository.delete(supplier);

    auditLogService.logAudit(
        AuditAction.DELETE,
        AuditResource.SUPPLIER,
        id,
        "Deleted supplier: " + supplier.getName());
  }

  public Map<String, Object> getSupplierLedger(Long id) {
    Supplier supplier = getById(id);

    List<com.ims.model.Order> orders = orderRepository.findBySupplierId(id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices = invoiceRepository.findBySupplierId(id);
    List<com.ims.model.Payment> payments = paymentRepository.findBySupplierId(id);

    return Map.of(
        "supplier", supplier,
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }
}
