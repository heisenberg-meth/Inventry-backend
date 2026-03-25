package com.ims.tenant.service;

import com.ims.tenant.domain.warehouse.WarehouseProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseProductRepository extends JpaRepository<WarehouseProduct, Long> {
  @Query(
      "SELECT wp FROM WarehouseProduct wp JOIN wp.product p "
          + "WHERE p.tenantId = :tenantId AND wp.storageLocation = :location AND p.isActive = true")
  Page<WarehouseProduct> findByTenantIdAndLocation(
      @Param("tenantId") Long tenantId, @Param("location") String location, Pageable pageable);
}
