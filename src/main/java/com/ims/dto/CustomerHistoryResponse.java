package com.ims.dto;

import com.ims.model.Order;
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
public class CustomerHistoryResponse {
  private Long customerId;
  private String customerName;
  private String customerEmail;
  private Integer totalOrders;
  private BigDecimal totalSpent;
  private Integer pendingOrders;
  private List<Order> orders;
}