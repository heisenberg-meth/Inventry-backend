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
  // findById is already provided by JpaRepository and will be tenant-scoped by Hibernate

  Page<Product> findByIsActiveTrue(Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM Product p WHERE p.id = :id")
  Optional<Product> findByIdWithLock(@Param("id") Long id);

  @Query("SELECT p FROM Product p WHERE p.stock <= p.reorderLevel AND p.isActive = true")
  List<Product> findLowStock();

  @Query(
      "SELECT p FROM Product p WHERE p.isActive = true AND "
          + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(p.barcode) LIKE LOWER(CONCAT('%', :query, '%')))")
  Page<Product> searchProducts(@Param("query") String query, Pageable pageable);

  @Query("SELECT COUNT(p) FROM Product p WHERE p.isActive = true")
  long countActive();

  @Query("SELECT COUNT(p) FROM Product p WHERE p.stock <= p.reorderLevel AND p.isActive = true")
  long countLowStock();

  @Query("SELECT COUNT(p) FROM Product p WHERE p.stock = 0 AND p.isActive = true")
  long countOutOfStock();

  boolean existsByCategoryId(Long categoryId);

  long countByCategoryId(Long categoryId);
}
