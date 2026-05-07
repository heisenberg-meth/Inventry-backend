package com.ims.order.dto;

import com.ims.order.entity.OrderStatus;
import com.ims.order.entity.OrderType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

  private Long id;
  private OrderType type;
  private OrderStatus status;
  private Long customerId;
  private Long supplierId;
  private BigDecimal totalAmount;
  private BigDecimal taxAmount;
  private BigDecimal discount;
  private String notes;
  private Long createdBy;
  private LocalDateTime createdAt;
  private List<OrderItemResponse> items;
}
