package com.ims.tenant.repository;

import com.ims.model.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

  @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId AND oi.tenantId = :tenantId")
  List<OrderItem> findByOrderIdAndTenantId(@Param("orderId") Long orderId, @Param("tenantId") Long tenantId);

  @Query("SELECT oi FROM OrderItem oi WHERE oi.orderId = :orderId")
  List<OrderItem> findByOrderId(@Param("orderId") Long orderId);

  @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
      "JOIN Order o ON oi.orderId = o.id " +
      "WHERE o.referenceOrderId = :originalOrderId AND oi.productId = :productId " +
      "AND o.type = 'RETURN'")
  int sumReturnedQty(@Param("originalOrderId") Long originalOrderId, @Param("productId") Long productId);
}
