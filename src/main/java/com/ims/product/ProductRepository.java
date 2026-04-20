package com.ims.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

  @Query("""
      SELECT p.id as id, p.name as name, p.sku as sku, p.barcode as barcode,
             p.categoryId as categoryId, p.unit as unit, p.purchasePrice as purchasePrice,
             p.salePrice as salePrice, p.stock as stock, p.reorderLevel as reorderLevel,
             p.isActive as isActive, p.createdAt as createdAt,
             pp.batchNumber as batchNumber, pp.expiryDate as expiryDate,
             pp.manufacturer as manufacturer, pp.hsnCode as hsnCode, pp.schedule as schedule,
             wp.storageLocation as storageLocation, wp.zone as zone,
             wp.rack as rack, wp.bin as bin
      FROM Product p
      LEFT JOIN PharmacyProduct pp ON pp.product.id = p.id
      LEFT JOIN WarehouseProduct wp ON wp.product.id = p.id
      WHERE p.tenantId = :tenantId AND p.isActive = true
      """)
  Page<ProductListView> findAllWithDetails(@Param("tenantId") Long tenantId, Pageable pageable);

  @Query("SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock, " +
      "p.reorderLevel as reorderLevel, p.salePrice as salePrice, p.unit as unit " +
      "FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true AND p.id > :lastId ORDER BY p.id")
  List<ProductListView> findNextProducts(@Param("tenantId") Long tenantId, @Param("lastId") Long lastId,
      Pageable pageable);

  @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true")
  List<Product> findLowStockByTenant(@Param("tenantId") Long tenantId);

  @Query(value = """
      SELECT * FROM products
      WHERE tenant_id = :tenantId AND is_active = true
      AND to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(sku, '') || ' ' || COALESCE(barcode, ''))
      @@ plainto_tsquery(:query)
      """, countQuery = """
      SELECT count(*) FROM products
      WHERE tenant_id = :tenantId AND is_active = true
      AND to_tsvector('english', COALESCE(name, '') || ' ' || COALESCE(sku, '') || ' ' || COALESCE(barcode, ''))
      @@ plainto_tsquery(:query)
      """, nativeQuery = true)
  Page<Product> searchFast(@Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true")
  long countActiveByTenant(@Param("tenantId") Long tenantId);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true")
  long countLowStockByTenant(@Param("tenantId") Long tenantId);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.reorderLevel AND p.isActive = true")
  long countLowStock();

  @Query("SELECT COUNT(p) FROM Product p WHERE p.stock = 0 AND p.isActive = true")
  long countOutOfStock();

  boolean existsByCategoryId(Long categoryId);

  boolean existsBySkuAndTenantId(String sku, Long tenantId);

  long countByCategoryId(Long categoryId);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
  long countActive();

  @Query("SELECT p FROM Product p WHERE p.tenantId = :tenantId AND p.stock < p.reorderLevel")
  List<Product> findLowStock(@Param("tenantId") Long tenantId);

  Page<Product> findByIsActiveTrue(Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :productId")
  Optional<Product> findByIdWithLock(@Param("productId") Long productId);
}