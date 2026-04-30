package com.ims.tenant.service;

import com.ims.dto.request.SupplierRequest;
import com.ims.dto.response.SupplierResponse;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.Payment;
import com.ims.model.Supplier;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.TenantContextException;
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
  private final AuditLogService auditLogService;

  public Page<SupplierResponse> getSuppliers(Pageable pageable) {
    TenantContext.assertTenantPresent();
    return Objects.requireNonNull(supplierRepository.findAll(pageable).map(this::toResponse));
  }

  public Supplier getById(Long id) {
    TenantContext.assertTenantPresent();
    return Objects.requireNonNull(
        supplierRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Supplier not found")));
  }

  public SupplierResponse getSupplierResponseById(Long id) {
    return Objects.requireNonNull(toResponse(getById(id)));
  }

  @Transactional
  public SupplierResponse create(
      SupplierRequest request) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant context is missing");
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
        Objects.requireNonNull(savedSupplier.getId()),
        "Created supplier: " + savedSupplier.getName());

    return Objects.requireNonNull(toResponse(savedSupplier));
  }

  @Transactional
  public SupplierResponse update(
      Long id, SupplierRequest updates) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant context is missing");
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
        Objects.requireNonNull(updatedSupplier.getId()),
        "Updated supplier: " + updatedSupplier.getName());

    return Objects.requireNonNull(toResponse(updatedSupplier));
  }

  @Transactional
  @RequiresPermission("delete_supplier")
  public void delete(Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new TenantContextException("Tenant context is missing");
    }

    Supplier supplier = getById(id);
    supplierRepository.delete(supplier);

    auditLogService.logAudit(
        AuditAction.DELETE, AuditResource.SUPPLIER, id, "Deleted supplier: " + supplier.getName());
  }

  public Map<String, Object> getSupplierLedger(Long id) {
    Supplier supplier = getById(id);

    List<Order> orders = orderRepository.findBySupplierId(id, Pageable.unpaged()).getContent();
    List<Invoice> invoices = invoiceRepository.findBySupplierId(id);
    List<Payment> payments = paymentRepository.findBySupplierId(id);

    return Objects.requireNonNull(
        Map.of(
            "supplier", toResponse(supplier),
            "orders", orders,
            "invoices", invoices,
            "payments", payments));
  }

  private SupplierResponse toResponse(Supplier supplier) {
    return Objects.requireNonNull(
        SupplierResponse.builder()
            .id(supplier.getId())
            .name(supplier.getName())
            .phone(supplier.getPhone())
            .email(supplier.getEmail())
            .address(supplier.getAddress())
            .gstin(supplier.getGstin())
            .build());
  }
}
