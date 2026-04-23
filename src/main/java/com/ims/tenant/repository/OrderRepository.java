package com.ims.tenant.repository;

import com.ims.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ims.tenant.dto.MonthlyRevenue;
import com.ims.tenant.dto.OrderStatusStat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByReferenceOrderId(Long referenceOrderId);

  // findById is inherited

  @Override
  @NonNull
  Page<Order> findAll(@NonNull Pageable pageable);

  @NonNull
  Page<Order> findByType(@NonNull String type, @NonNull Pageable pageable);

  @NonNull
  Page<Order> findBySupplierId(@NonNull Long supplierId, @NonNull Pageable pageable);

  @NonNull
  Page<Order> findByCustomerId(@NonNull Long customerId, @NonNull Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  @NonNull
  BigDecimal sumAmountByTypeAndDateRange(
      @Param("type") @NonNull String type,
      @Param("from") @NonNull LocalDateTime from,
      @Param("to") @NonNull LocalDateTime to);

  @Query(
      "SELECT COALESCE(SUM(o.taxAmount), 0) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  @NonNull
  BigDecimal sumTaxAmountByTypeAndDateRange(
      @Param("type") @NonNull String type,
      @Param("from") @NonNull LocalDateTime from,
      @Param("to") @NonNull LocalDateTime to);

  @Query(
      "SELECT COUNT(o) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  long countByTypeAndDateRange(
      @Param("type") @NonNull String type,
      @Param("from") @NonNull LocalDateTime from,
      @Param("to") @NonNull LocalDateTime to);

  @Query("""
      SELECT new com.ims.tenant.dto.MonthlyRevenue(YEAR(o.createdAt), MONTH(o.createdAt), SUM(o.totalAmount))
      FROM Order o
      WHERE o.type = :type
        AND o.tenantId = :tenantId
        AND o.createdAt >= :from
      GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)
      ORDER BY YEAR(o.createdAt), MONTH(o.createdAt)
      """)
  List<MonthlyRevenue> getMonthlyRevenue(
      @Param("type") String type,
      @Param("tenantId") Long tenantId,
      @Param("from") LocalDateTime from);

  @Query("""
      SELECT new com.ims.tenant.dto.OrderStatusStat(o.status, COUNT(o))
      FROM Order o
      WHERE o.tenantId = :tenantId
        AND o.createdAt >= :from
      GROUP BY o.status
      """)
  List<OrderStatusStat> getOrderStatusStats(
      @Param("tenantId") Long tenantId,
      @Param("from") LocalDateTime from);
}
