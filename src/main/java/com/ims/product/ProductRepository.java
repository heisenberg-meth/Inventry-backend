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
                               p.isDeleted as isDeleted, p.createdAt as createdAt,
                               pp.batchNumber as batchNumber, pp.expiryDate as expiryDate,
                               pp.manufacturer as manufacturer, pp.hsnCode as hsnCode, pp.schedule as schedule,
                               wp.storageLocation as storageLocation, wp.zone as zone,
                               wp.rack as rack, wp.bin as bin
                        FROM Product p
                        LEFT JOIN PharmacyProduct pp ON pp.product.id = p.id
                        LEFT JOIN WarehouseProduct wp ON wp.product.id = p.id
                        WHERE p.isDeleted = false
                        """)
        Page<ProductListView> findAllWithDetails(Pageable pageable);

        @Query("SELECT p.id as id, p.name as name, p.sku as sku, p.stock as stock, " +
                        "p.reorderLevel as reorderLevel, p.salePrice as salePrice, p.unit as unit " +
                        "FROM Product p WHERE p.isDeleted = false AND p.id > :lastId ORDER BY p.id")
        List<ProductListView> findNextProducts(@Param("lastId") Long lastId, Pageable pageable);

        @Query(value = """
                        SELECT * FROM products
                        WHERE tenant_id = :tenantId
                        AND is_deleted = false
                        AND search_vector @@ plainto_tsquery(:query)
                        """, countQuery = """
                        SELECT count(*) FROM products
                        WHERE tenant_id = :tenantId
                        AND is_deleted = false
                        AND search_vector @@ plainto_tsquery(:query)
                        """, nativeQuery = true)
        Page<Product> searchFast(@Param("tenantId") Long tenantId, @Param("query") String query, Pageable pageable);

        @Query("SELECT COUNT(p) FROM Product p WHERE p.isDeleted = false")
        long countActive();

        @Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.reorderLevel AND p.isDeleted = false")
        long countLowStock();

        @Query("SELECT COUNT(p) FROM Product p WHERE p.stock = 0 AND p.isDeleted = false")
        long countOutOfStock();

        boolean existsByCategoryId(Long categoryId);

        boolean existsBySku(String sku);

        long countByCategoryId(Long categoryId);

        @Query("SELECT p FROM Product p WHERE p.stock < p.reorderLevel AND p.isDeleted = false")
        List<Product> findLowStock();

        Page<Product> findByIsDeletedFalse(Pageable pageable);

        @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Product p WHERE p.id = :productId AND p.tenantId = :tenantId AND p.isDeleted = false")
        Optional<Product> findByIdWithLock(@Param("productId") Long productId, @Param("tenantId") Long tenantId);

        @Query("SELECT p FROM Product p WHERE p.id = :id AND p.isDeleted = false")
        Optional<Product> findByIdAndIsDeletedFalse(@Param("id") Long id);
}
