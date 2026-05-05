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
}
