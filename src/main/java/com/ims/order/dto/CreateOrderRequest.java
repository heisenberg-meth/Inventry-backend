package com.ims.order.dto;

import com.ims.order.entity.OrderType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

  @NotNull private OrderType type;

  private Long customerId;

  private Long supplierId;

  private BigDecimal discount;

  private String notes;

  @NotEmpty private List<OrderItemRequest> items;
}
