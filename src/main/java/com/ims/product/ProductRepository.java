package com.ims.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

       Page<Product> findAllByActiveTrue(Pageable pageable);

       @Query("""
                     SELECT p.id as id, p.name as name, p.sku as sku, p.barcode as barcode,
                            p.categoryId as categoryId, p.unit as unit, p.purchasePrice as purchasePrice,
                            p.salePrice as salePrice, p.stock as stock, p.reorderLevel as reorderLevel,
                            p.active as isActive, p.createdAt as createdAt,
                            pp.batchNumber as batchNumber, pp.expiryDate as expiryDate,
                            pp.manufacturer as manufacturer, pp.hsnCode as hsnCode, pp.schedule as schedule,
                            wp.storageLocation as storageLocation, wp.zone as zone,
                            wp.rack as rack, wp.bin as bin
                     FROM Product p
                     LEFT JOIN PharmacyProduct pp ON pp.product.id = p.id
                     LEFT JOIN WarehouseProduct wp ON wp.product.id = p.id
                     WHERE p.active = true
                     """)
       Page<ProductListView> findAllWithDetails(Pageable pageable);

       @Query("""
                     SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock,
                            p.reorderLevel as reorderLevel, p.unit as unit, pp.expiryDate as expiryDate
                     FROM Product p
                     LEFT JOIN PharmacyProduct pp ON pp.product.id = p.id
                     WHERE p.active = true
                     """)
       List<ProductReportView> findStockReportView();

       @Query("""
                     SELECT p.id as id, p.name as name, p.sku as sku, p.barcode as barcode,
                            p.categoryId as categoryId, p.unit as unit, p.purchasePrice as purchasePrice,
                            p.salePrice as salePrice, p.stock as stock, p.reorderLevel as reorderLevel,
                            p.active as isActive, p.createdAt as createdAt,
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

       @Query("SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock, "
                     + "p.reorderLevel as reorderLevel, p.salePrice as salePrice, p.unit as unit "
                     + "FROM Product p WHERE p.active = true AND p.id > :lastId ORDER BY p.id")
       List<ProductListView> findNextProducts(
                     @Param("lastId") Long lastId, Pageable pageable);

       @Query("""
                     SELECT p.id as id, p.name as name, p.sku as sku, p.barcode as barcode,
                            p.categoryId as categoryId, p.unit as unit, p.purchasePrice as purchasePrice,
                            p.salePrice as salePrice, p.stock as stock, p.reorderLevel as reorderLevel,
                            p.active as isActive, p.createdAt as createdAt,
                            pp.batchNumber as batchNumber, pp.expiryDate as expiryDate,
                            pp.manufacturer as manufacturer, pp.hsnCode as hsnCode, pp.schedule as schedule,
                            wp.storageLocation as storageLocation, wp.zone as zone,
                            wp.rack as rack, wp.bin as bin
                     FROM Product p
                     LEFT JOIN PharmacyProduct pp ON pp.product.id = p.id
                     LEFT JOIN WarehouseProduct wp ON wp.product.id = p.id
                     WHERE p.stock <= p.reorderLevel AND p.active = true
                     """)
       List<ProductListView> findLowStockByTenant();

       @Query(value = """
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
                     AND p.search_vector @@ plainto_tsquery(:query)
                     """, countQuery = """
                     SELECT count(*) FROM products p
                     WHERE p.tenant_id = :tenantId AND p.is_active = true
                     AND p.search_vector @@ plainto_tsquery(:query)
                     """, nativeQuery = true)
       Page<ProductListView> searchFast(
                     @Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);

       @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
       long countActiveByTenant();

       @Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.reorderLevel AND p.active = true")
       long countLowStockByTenant();

       @Query("SELECT COUNT(p) FROM Product p WHERE p.stock = 0 AND p.active = true")
       long countOutOfStockByTenant();

       @Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.reorderLevel AND p.active = true")
       long countLowStock();

       @Query("SELECT p FROM Product p WHERE p.stock <= p.reorderLevel AND p.active = true")
       Stream<Product> streamAllLowStock();

       @Query("SELECT p FROM Product p WHERE p.stock <= p.reorderLevel AND p.active = true")
       Stream<Product> streamAllLowStockGlobal();

       @Query("SELECT COUNT(p) FROM Product p WHERE p.stock = 0 AND p.active = true")
       long countOutOfStock();

       boolean existsByCategoryId(Long categoryId);

       boolean existsBySku(String sku);

       long countByCategoryId(Long categoryId);

       @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
       long countActive();

       @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
       long countActiveGlobal();

       @Query("SELECT p.id as id, p.name as name, p.stock as stock FROM Product p WHERE p.stock < p.reorderLevel")
       List<ProductStockView> findLowStock();

       @Query("SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock, p.salePrice as salePrice, p.categoryId as categoryId "
                     + "FROM Product p WHERE p.active = true")
       List<ProductExportView> findExportData();

       Page<Product> findByActiveTrue(Pageable pageable);

       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM Product p WHERE p.id = :productId")
       Optional<Product> findByIdWithLock(@Param("productId") Long productId);

       /**
        * Calculates the total inventory valuation for a tenant.
        * Basis: Current Sale Price * Current Stock Level.
        */
       @Query("""
                     SELECT COALESCE(SUM(p.salePrice * p.stock), 0)
                     FROM Product p
                     WHERE p.active = true
                     """)
       BigDecimal getTotalInventoryValue();

       /**
        * Calculates the total inventory cost value for a tenant.
        * Basis: Current Purchase Price * Current Stock Level.
        */
       @Query("""
                     SELECT COALESCE(SUM(p.purchasePrice * p.stock), 0)
                     FROM Product p
                     WHERE p.active = true
                     """)
       BigDecimal getTotalInventoryCost();

       @Query("""
                     SELECT c.name as categoryName, COUNT(p) as productCount
                     FROM Product p
                     JOIN Category c ON p.categoryId = c.id
                     WHERE p.active = true
                     GROUP BY c.name
                     """)
       List<CategoryCount> getCategoryDistribution();

       @Query("""
                     SELECT COALESCE(SUM(p.stock), 0)
                     FROM Product p
                     WHERE p.active = true
                     """)
       Long getTotalStock();

       @Query("""
                     SELECT p.id as id, p.name as name, p.stock as stock
                     FROM Product p
                     WHERE p.active = true
                     ORDER BY p.stock DESC
                     """)
       Page<ProductStockView> findTopStock(Pageable pageable);

       @Modifying(clearAutomatically = true)
       @Query("UPDATE Product p SET p.stock = p.stock + :qty, p.version = p.version + 1, p.updatedAt = :now WHERE p.id = :productId")
       int incrementStock(
                     @Param("productId") Long productId, @Param("qty") int qty, @Param("now") LocalDateTime now);

       @Modifying(clearAutomatically = true)
       @Query("UPDATE Product p SET p.stock = p.stock - :qty, p.version = p.version + 1, p.updatedAt = :now WHERE p.id = :productId AND p.stock >= :qty")
       int decrementStockIfAvailable(
                     @Param("productId") Long productId, @Param("qty") int qty, @Param("now") LocalDateTime now);

       @Modifying(clearAutomatically = true)
       @Query("UPDATE Product p SET p.stock = p.stock + :qty, p.version = p.version + 1, p.updatedAt = :now WHERE p.id = :productId AND (p.stock + :qty) >= 0")
       int adjustStockIfValid(
                     @Param("productId") Long productId, @Param("qty") int qty, @Param("now") LocalDateTime now);
}
