package com.ims.tenant.repository;

import com.ims.model.Order;
import com.ims.tenant.dto.MonthlyRevenue;
import com.ims.tenant.dto.OrderStatusStat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT o FROM Order o WHERE o.id = :id")
  java.util.Optional<Order> lockById(@Param("id") Long id);

  @Query("SELECT o FROM Order o WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  Page<Order> findByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

  @Query("SELECT o FROM Order o WHERE o.createdAt >= :from AND o.createdAt <= :to")
  Page<Order> findByDateRange(
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      Pageable pageable);

  List<Order> findByReferenceOrderId(Long referenceOrderId);

  // findById is inherited

  @Override
  Page<Order> findAll(Pageable pageable);

  Page<Order> findByType(String type, Pageable pageable);

  Page<Order> findBySupplierId(Long supplierId, Pageable pageable);

  Page<Order> findByCustomerId(Long customerId, Pageable pageable);

  @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o "
      + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  BigDecimal sumAmountByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("SELECT COALESCE(SUM(o.taxAmount), 0) FROM Order o "
      + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  BigDecimal sumTaxAmountByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("SELECT COUNT(o) FROM Order o "
      + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  long countByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query("""
      SELECT YEAR(o.createdAt) as year, MONTH(o.createdAt) as month, SUM(o.totalAmount) as revenue
      FROM Order o
      WHERE o.type = :type
        AND o.createdAt >= :from
      GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)
      ORDER BY YEAR(o.createdAt), MONTH(o.createdAt)
      """)
  List<MonthlyRevenue> getMonthlyRevenue(
      @Param("type") String type,
      @Param("from") LocalDateTime from);

  @Query("""
      SELECT o.status as status, COUNT(o) as count
      FROM Order o
      WHERE o.createdAt >= :from
      GROUP BY o.status
      """)
  List<OrderStatusStat> getOrderStatusStats(@Param("from") LocalDateTime from);

  java.util.Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
