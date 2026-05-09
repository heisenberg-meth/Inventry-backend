package com.ims.tenant.repository;

import com.ims.model.Inventory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

  @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.tenantId = :tenantId")
  Optional<Inventory> findByProductIdAndTenantId(
      @Param("productId") Long productId, @Param("tenantId") Long tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM Inventory i WHERE i.productId = :productId AND i.tenantId = :tenantId")
  Optional<Inventory> findByProductIdAndTenantIdWithLock(
      @Param("productId") Long productId, @Param("tenantId") Long tenantId);

  @Query("SELECT i FROM Inventory i WHERE i.tenantId = :tenantId")
  Page<Inventory> findAllByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query(
      "SELECT i FROM Inventory i WHERE i.tenantId = :tenantId AND i.quantity <= i.lowStockThreshold")
  Page<Inventory> findLowStockByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query("SELECT i FROM Inventory i WHERE i.tenantId = :tenantId AND i.quantity <= i.reorderLevel")
  Page<Inventory> findBelowReorderLevelByTenantId(
      @Param("tenantId") Long tenantId, Pageable pageable);

  @Query(
      "SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Inventory i WHERE i.productId = :productId AND i.tenantId = :tenantId")
  boolean existsByProductIdAndTenantId(
      @Param("productId") Long productId, @Param("tenantId") Long tenantId);
}
