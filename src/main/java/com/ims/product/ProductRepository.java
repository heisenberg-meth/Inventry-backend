package com.ims.product;

import java.math.BigDecimal;
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

  @Query(
      """
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

  @Query(
      """
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
      WHERE p.id = :id
      """)
  Optional<ProductListView> findByIdWithDetails(@Param("id") Long id);

  @Query(
      "SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock, "
          + "p.reorderLevel as reorderLevel, p.salePrice as salePrice, p.unit as unit "
          + "FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true AND p.id > :lastId ORDER BY p.id")
  List<ProductListView> findNextProducts(
      @Param("tenantId") Long tenantId, @Param("lastId") Long lastId, Pageable pageable);

  @Query(
      """
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
      WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true
      """)
  List<ProductListView> findLowStockByTenant(@Param("tenantId") Long tenantId);

  @Query(
      value =
          """
      SELECT p.id as id, p.name as name, p.sku as sku, p.barcode as barcode,
             p.category_id as categoryId, p.unit as unit, p.purchase_price as purchasePrice,
             p.sale_price as salePrice, p.stock as stock, p.reorder_level as reorderLevel,
             p.is_active as isActive, p.created_at as createdAt,
             pp.batch_number as batchNumber, pp.expiry_date as expiryDate,
             pp.manufacturer as manufacturer, pp.hsn_code as hsnCode, pp.schedule as schedule,
             wp.storage_location as storageLocation, wp.zone as zone,
             wp.rack as rack, wp.bin as bin
      FROM products p
      LEFT JOIN pharmacy_products pp ON pp.product_id = p.id
      LEFT JOIN warehouse_products wp ON wp.product_id = p.id
      WHERE p.tenant_id = :tenantId AND p.is_active = true
      AND to_tsvector('english', COALESCE(p.name, '') || ' ' || COALESCE(p.sku, '') || ' ' || COALESCE(p.barcode, ''))
      @@ plainto_tsquery(:query)
      """,
      countQuery =
          """
      SELECT count(*) FROM products p
      WHERE p.tenant_id = :tenantId AND p.is_active = true
      AND to_tsvector('english', COALESCE(p.name, '') || ' ' || COALESCE(p.sku, '') || ' ' || COALESCE(p.barcode, ''))
      @@ plainto_tsquery(:query)
      """,
      nativeQuery = true)
  Page<ProductListView> searchFast(
      @Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.isActive = true")
  long countActiveByTenant(@Param("tenantId") Long tenantId);

  @Query(
      "SELECT COUNT(p) FROM Product p WHERE p.tenantId = :tenantId AND p.stock <= p.reorderLevel AND p.isActive = true")
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

  @Query(
      """
      SELECT COALESCE(SUM(p.salePrice * p.stock), 0)
      FROM Product p
      WHERE p.tenantId = :tenantId AND p.isActive = true
      """)
  BigDecimal getTotalInventoryValue(@Param("tenantId") Long tenantId);

  @Query(
      """
      SELECT new com.ims.product.CategoryCount(c.name, COUNT(p))
      FROM Product p
      JOIN Category c ON p.categoryId = c.id
      WHERE p.tenantId = :tenantId AND p.isActive = true
      GROUP BY c.name
      """)
  List<CategoryCount> getCategoryDistribution(@Param("tenantId") Long tenantId);

  @Query(
      """
      SELECT COALESCE(SUM(p.stock), 0)
      FROM Product p
      WHERE p.tenantId = :tenantId AND p.isActive = true
      """)
  Long getTotalStock(@Param("tenantId") Long tenantId);

  @Query(
      """
      SELECT new com.ims.product.ProductStockView(p.id, p.name, p.stock)
      FROM Product p
      WHERE p.tenantId = :tenantId AND p.isActive = true
      ORDER BY p.stock DESC
      """)
  Page<ProductStockView> findTopStock(@Param("tenantId") Long tenantId, Pageable pageable);
}
