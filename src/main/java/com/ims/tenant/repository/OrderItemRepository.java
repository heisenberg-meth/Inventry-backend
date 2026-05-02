package com.ims.tenant.repository;

import com.ims.model.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
        List<OrderItem> findByOrderId(Long orderId);

        List<OrderItem> findByOrderIdIn(List<Long> orderIds);

        @Modifying(clearAutomatically = true)
        @Query("UPDATE OrderItem oi SET oi.returnedQuantity = oi.returnedQuantity + :qty "
                        + "WHERE oi.id = :id AND oi.returnedQuantity + :qty <= oi.quantity")
        int incrementReturnedQuantity(
                        @Param("id") Long id,
                        @Param("qty") Integer qty);
}
