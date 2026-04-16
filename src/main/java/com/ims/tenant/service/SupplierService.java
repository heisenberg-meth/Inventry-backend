package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.shared.rbac.RequiresPermission;
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
import org.springframework.lang.NonNull;
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

  public @NonNull Page<Supplier> getSuppliers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(supplierRepository.findAll(pageable));
  }

  public @NonNull Supplier getById(@NonNull Long id) {
    return Objects.requireNonNull(supplierRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found")));
  }

  @Transactional
  public @NonNull Supplier create(@NonNull Supplier supplier) {
    Supplier savedSupplier = Objects.requireNonNull(supplierRepository.save(supplier));

    auditLogService.logAudit(
        "CREATE",
        "SUPPLIER",
        savedSupplier.getId(),
        "Created supplier: " + savedSupplier.getName());

    return savedSupplier;
  }

  @Transactional
  public @NonNull Supplier update(@NonNull Long id, @NonNull Supplier updates) {
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
        "UPDATE",
        "SUPPLIER",
        updatedSupplier.getId(),
        "Updated supplier: " + updatedSupplier.getName());

    return updatedSupplier;
  }

  @Transactional
  @RequiresPermission("delete_supplier")
  public void delete(@NonNull Long id) {
    Supplier supplier = getById(id);
    supplierRepository.delete(supplier);

    auditLogService.logAudit(
        "DELETE",
        "SUPPLIER",
        id,
        "Deleted supplier: " + supplier.getName());
  }

  public Map<String, Object> getSupplierLedger(@NonNull Long id) {
    Supplier supplier = getById(id);

    List<com.ims.model.Order> orders = orderRepository.findBySupplierId(id, Pageable.unpaged()).getContent();
    List<com.ims.model.Invoice> invoices = invoiceRepository.findBySupplierId(id);
    List<com.ims.model.Payment> payments = paymentRepository.findBySupplierId(id);

    return Map.of(
        "supplier", supplier,
        "orders", orders,
        "invoices", invoices,
        "payments", payments
    );
  }
}
