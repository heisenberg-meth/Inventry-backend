package com.ims.tenant.domain.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PharmacyProductRepository extends JpaRepository<PharmacyProduct, Long> {
    List<PharmacyProduct> findByExpiryDateBefore(LocalDate date);

    @Query("SELECT pp FROM PharmacyProduct pp JOIN pp.product p " +
           "WHERE p.tenantId = :tenantId AND pp.expiryDate <= :date AND p.isActive = true")
    List<PharmacyProduct> findExpiringByTenantId(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(pp) FROM PharmacyProduct pp JOIN pp.product p " +
           "WHERE p.tenantId = :tenantId AND pp.expiryDate <= :date AND p.isActive = true")
    long countExpiringByTenantId(@Param("tenantId") Long tenantId, @Param("date") LocalDate date);
}
