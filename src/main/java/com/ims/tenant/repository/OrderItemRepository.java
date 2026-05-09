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

  @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi " +
      "WHERE oi.tenantId = :tenantId AND oi.productId = :productId AND EXISTS (" +
      "    SELECT 1 FROM Order o WHERE o.id = oi.orderId AND o.referenceOrderId = :originalOrderId " +
      "    AND o.type = 'RETURN'" +
      ")")
  int sumReturnedQtyByTenantId(@Param("originalOrderId") Long originalOrderId, @Param("tenantId") Long tenantId,
      @Param("productId") Long productId);
}
