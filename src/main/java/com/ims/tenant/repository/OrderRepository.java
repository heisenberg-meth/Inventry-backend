package com.ims.tenant.repository;

import com.ims.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId")
  Page<Order> findAllByTenantId(@Param("tenantId") Long tenantId, @NonNull Pageable pageable);

  @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId AND o.type = :type")
  Page<Order> findByTenantIdAndType(@Param("tenantId") Long tenantId, @Param("type") String type, Pageable pageable);

  @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId AND o.supplierId = :supplierId")
  Page<Order> findByTenantIdAndSupplierId(@Param("tenantId") Long tenantId, @Param("supplierId") Long supplierId, Pageable pageable);

  @Query("SELECT o FROM Order o WHERE o.tenantId = :tenantId AND o.customerId = :customerId")
  Page<Order> findByTenantIdAndCustomerId(@Param("tenantId") Long tenantId, @Param("customerId") Long customerId, Pageable pageable);

  @Query("SELECT o FROM Order o WHERE o.id = :id AND o.tenantId = :tenantId")
  java.util.Optional<Order> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);

  @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o "
      + "WHERE o.tenantId = :tenantId AND o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  BigDecimal sumAmountByTenantIdAndTypeAndDateRange(
      @Param("tenantId") Long tenantId,
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("SELECT COALESCE(SUM(o.taxAmount), 0) FROM Order o "
      + "WHERE o.tenantId = :tenantId AND o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  BigDecimal sumTaxAmountByTenantIdAndTypeAndDateRange(
      @Param("tenantId") Long tenantId,
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("SELECT COUNT(o) FROM Order o "
      + "WHERE o.tenantId = :tenantId AND o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  long countByTenantIdAndTypeAndDateRange(
      @Param("tenantId") Long tenantId,
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  Page<Order> findBySupplierId(Long supplierId, Pageable pageable);

  Page<Order> findByCustomerId(Long customerId, Pageable pageable);

  Page<Order> findByType(String type, Pageable pageable);

  @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.type = :type AND o.createdAt BETWEEN :start AND :end")
  BigDecimal sumAmountByTypeAndDateRange(@Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query("SELECT COUNT(o) FROM Order o WHERE o.type = :type AND o.createdAt BETWEEN :start AND :end")
  long countByTypeAndDateRange(@Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

  @Query("SELECT SUM(o.taxAmount) FROM Order o WHERE o.type = :type AND o.createdAt BETWEEN :start AND :end")
  BigDecimal sumTaxAmountByTypeAndDateRange(@Param("type") String type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
