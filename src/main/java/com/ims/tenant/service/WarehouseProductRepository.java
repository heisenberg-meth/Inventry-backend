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
          + "WHERE wp.storageLocation = :location AND p.active = true")
  Page<WarehouseProduct> findByLocation(@Param("location") String location, Pageable pageable);
}
