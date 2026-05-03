package com.ims.tenant.repository;

import com.ims.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @Query("SELECT s FROM Supplier s WHERE s.tenantId = :tenantId")
    Page<Supplier> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.tenantId = :tenantId")
    List<Supplier> findAllByTenantId(@Param("tenantId") Long tenantId);
}
