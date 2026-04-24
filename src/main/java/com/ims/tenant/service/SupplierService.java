package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import com.ims.tenant.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

  private final SupplierRepository supplierRepository;
  private final OrderRepository orderRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentRepository paymentRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  public @NonNull Page<com.ims.dto.response.SupplierResponse> getSuppliers(
      @NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.getSuppliers");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(supplierRepository.findAll(pageable).map(this::toResponse));
  }

  public @NonNull Supplier getById(@NonNull Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.getById");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(
        supplierRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found")));
  }

  public @NonNull com.ims.dto.response.SupplierResponse getSupplierResponseById(@NonNull Long id) {
    return toResponse(getById(id));
  }

  @Transactional
  public @NonNull com.ims.dto.response.SupplierResponse create(
      @NonNull com.ims.dto.request.SupplierRequest request) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.create");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    Supplier supplier = new Supplier();
    supplier.setName(request.getName());
    supplier.setPhone(request.getPhone());
    supplier.setEmail(request.getEmail());
    supplier.setAddress(request.getAddress());
    supplier.setGstin(request.getGstin());
    Supplier savedSupplier = Objects.requireNonNull(supplierRepository.save(supplier));

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.SUPPLIER,
        savedSupplier.getId(),
        "Created supplier: " + savedSupplier.getName());

    return toResponse(savedSupplier);
  }

  @Transactional
  public @NonNull com.ims.dto.response.SupplierResponse update(
      @NonNull Long id, @NonNull com.ims.dto.request.SupplierRequest updates) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.update");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

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

    return toResponse(updatedSupplier);
  }

  @Transactional
  @RequiresPermission("delete_supplier")
  public void delete(@NonNull Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.delete");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    Supplier supplier = getById(id);
    supplierRepository.delete(supplier);

    auditLogService.logAudit(
        AuditAction.DELETE, AuditResource.SUPPLIER, id, "Deleted supplier: " + supplier.getName());
  }

  public Map<String, Object> getSupplierLedger(@NonNull Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in SupplierService.getSupplierLedger");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    Supplier supplier = getById(id);

    List<com.ims.model.Order> orders =
        orderRepository.findBySupplierId(id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices = invoiceRepository.findBySupplierId(id);
    List<com.ims.model.Payment> payments = paymentRepository.findBySupplierId(id);

    return Map.of(
        "supplier", toResponse(supplier),
        "orders", orders,
        "invoices", invoices,
        "payments", payments);
  }

  private com.ims.dto.response.SupplierResponse toResponse(Supplier supplier) {
    return com.ims.dto.response.SupplierResponse.builder()
        .id(supplier.getId())
        .name(supplier.getName())
        .phone(supplier.getPhone())
        .email(supplier.getEmail())
        .address(supplier.getAddress())
        .gstin(supplier.getGstin())
        .build();
  }
}
