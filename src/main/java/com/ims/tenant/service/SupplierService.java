package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.ResourceNotFoundException;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import com.ims.tenant.repository.SupplierRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
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
    Long tenantId = TenantContext.requireTenantId();
    return Objects.requireNonNull(supplierRepository.findAllActiveByTenantId(tenantId, pageable));
  }

  public Supplier getById(Long id) {
    Long tenantId = TenantContext.requireTenantId();
    return Objects.requireNonNull(
        supplierRepository
            .findActiveByIdAndTenantId(id, tenantId)
            .orElseThrow(
                () -> new ResourceNotFoundException("Supplier not found or has been deleted")));
  }

  @Transactional
  public Supplier create(Supplier supplier) {
    Long tenantId = TenantContext.requireTenantId();
    supplier.setTenantId(tenantId);

    if (supplier.getEmail() != null
        && supplierRepository.existsByTenantIdAndEmailAndIsDeletedFalse(
            tenantId, supplier.getEmail())) {
      throw new IllegalArgumentException("Supplier with this email already exists for this tenant");
    }
    if (supplier.getPhone() != null
        && supplierRepository.existsByTenantIdAndPhoneAndIsDeletedFalse(
            tenantId, supplier.getPhone())) {
      throw new IllegalArgumentException("Supplier with this phone already exists for this tenant");
    }

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
    Long tenantId = TenantContext.requireTenantId();

    if (updates.getEmail() != null && !updates.getEmail().equals(supplier.getEmail())) {
      if (supplierRepository.existsByTenantIdAndEmailAndIsDeletedFalse(
          tenantId, updates.getEmail())) {
        throw new IllegalArgumentException("Supplier with this email already exists");
      }
      supplier.setEmail(updates.getEmail());
    }
    if (updates.getPhone() != null && !updates.getPhone().equals(supplier.getPhone())) {
      if (supplierRepository.existsByTenantIdAndPhoneAndIsDeletedFalse(
          tenantId, updates.getPhone())) {
        throw new IllegalArgumentException("Supplier with this phone already exists");
      }
      supplier.setPhone(updates.getPhone());
    }

    if (updates.getName() != null) {
      supplier.setName(updates.getName());
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
    supplier.setIsDeleted(true);
    supplierRepository.save(supplier);

    auditLogService.logAudit(
        AuditAction.DELETE,
        AuditResource.SUPPLIER,
        id,
        "Soft-deleted supplier: " + supplier.getName());
  }

  public Map<String, Object> getSupplierLedger(Long id) {
    Supplier supplier = getById(id);
    Long tenantId = TenantContext.requireTenantId();

    List<com.ims.model.Order> orders =
        orderRepository.findByTenantIdAndSupplierId(tenantId, id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices =
        invoiceRepository.findByTenantIdAndSupplierId(tenantId, id);
    List<com.ims.model.Payment> payments =
        paymentRepository.findByTenantIdAndSupplierId(tenantId, id);

    return Map.of(
        "supplier", supplier,
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }
}
