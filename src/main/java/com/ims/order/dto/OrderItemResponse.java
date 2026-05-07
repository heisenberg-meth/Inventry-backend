package com.ims.order.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

  private Long id;
  private Long productId;
  private String productName;
  private String sku;
  private Integer quantity;
  private BigDecimal unitPrice;
  private BigDecimal discount;
  private BigDecimal taxRate;
  private BigDecimal subtotal;
}
