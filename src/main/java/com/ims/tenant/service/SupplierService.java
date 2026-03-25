package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.SupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupplierService {

  private final SupplierRepository supplierRepository;

  public Page<Supplier> getSuppliers(Pageable pageable) {
    return supplierRepository.findByTenantId(TenantContext.get(), pageable);
  }

  public Supplier getById(Long id) {
    return supplierRepository
        .findByIdAndTenantId(id, TenantContext.get())
        .orElseThrow(() -> new EntityNotFoundException("Supplier not found"));
  }

  @Transactional
  public Supplier create(Supplier supplier) {
    supplier.setTenantId(TenantContext.get());
    return supplierRepository.save(supplier);
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
    return supplierRepository.save(supplier);
  }

  @Transactional
  public void delete(Long id) {
    Supplier supplier = getById(id);
    supplierRepository.delete(supplier);
  }
}
