package com.ims.tenant.repository;

import com.ims.model.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

  @Query("SELECT s FROM Supplier s WHERE s.tenantId = :tenantId AND s.isDeleted = false")
  Page<Supplier> findAllActiveByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  boolean existsByTenantIdAndEmailAndIsDeletedFalse(Long tenantId, String email);

  boolean existsByTenantIdAndPhoneAndIsDeletedFalse(Long tenantId, String phone);

  @Query("SELECT s FROM Supplier s WHERE s.id = :id AND s.tenantId = :tenantId AND s.isDeleted = false")
  Optional<Supplier> findActiveByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

  @Query("SELECT s FROM Supplier s WHERE s.id = :id AND s.isDeleted = false")
  Optional<Supplier> findActiveById(@Param("id") Long id);
}
