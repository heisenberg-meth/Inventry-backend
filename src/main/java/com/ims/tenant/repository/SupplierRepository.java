package com.ims.tenant.repository;

import com.ims.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);
    Page<Supplier> findByTenantId(Long tenantId, Pageable pageable);
}
