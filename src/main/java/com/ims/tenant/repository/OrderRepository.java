package com.ims.tenant.repository;

import com.ims.model.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
  // findById is inherited

  Page<Order> findAll(Pageable pageable);

  Page<Order> findByType(String type, Pageable pageable);

  @Query(
      "SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  BigDecimal sumAmountByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);

  @Query(
      "SELECT COUNT(o) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  long countByTypeAndDateRange(
      @Param("type") String type,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
