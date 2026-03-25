package com.ims.tenant.repository;

import com.ims.model.StockMovement;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

  Page<StockMovement> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

  @Query(
      "SELECT sm FROM StockMovement sm WHERE sm.tenantId = :tenantId "
          + "AND (:productId IS NULL OR sm.productId = :productId) "
          + "AND (:from IS NULL OR sm.createdAt >= :from) "
          + "AND (:to IS NULL OR sm.createdAt <= :to) "
          + "ORDER BY sm.createdAt DESC")
  Page<StockMovement> findByFilters(
      @Param("tenantId") Long tenantId,
      @Param("productId") Long productId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);
}
