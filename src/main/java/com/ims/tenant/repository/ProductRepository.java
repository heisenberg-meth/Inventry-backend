package com.ims.tenant.repository;

import com.ims.model.Product;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
  Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

  Page<Product> findByTenantIdAndIsActiveTrue(Long tenantId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id AND p.tenantId = :tenantId")
  Optional<Product> findByIdAndTenantIdWithLock(
      @Param("id") Long id, @Param("tenantId") Long tenantId);

  @Query(
      "SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true")
  List<Product> findLowStockByTenantId(@Param("tenantId") Long tenantId);

  @Query(
      "SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true AND "
          + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :query, '%')))")
  Page<Product> searchProducts(
      @Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true")
  long countActiveByTenantId(@Param("tenantId") Long tenantId);

  @Query(
      "SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true")
  long countLowStockByTenantId(@Param("tenantId") Long tenantId);

  @Query(
      "SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.stock = 0 AND p.isActive = true")
  long countOutOfStockByTenantId(@Param("tenantId") Long tenantId);

  boolean existsByCategoryIdAndTenantId(Long categoryId, Long tenantId);

  long countByCategoryIdAndTenantId(Long categoryId, Long tenantId);
}
