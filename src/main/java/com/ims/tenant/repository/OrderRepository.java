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
      "SELECT COUNT(o) FROM Order o "
          + "WHERE o.type = :type AND o.createdAt >= :from AND o.createdAt <= :to")
  long countByTypeAndDateRange(
      @Param("type") @NonNull String type,
      @Param("from") @NonNull LocalDateTime from,
      @Param("to") @NonNull LocalDateTime to);
}
