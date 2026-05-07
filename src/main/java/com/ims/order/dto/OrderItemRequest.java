package com.ims.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemRequest {

  @NotNull
  private Long productId;

  @NotNull
  @Positive
  private Integer quantity;

  @DecimalMin(value = "0.00", message = "Unit price must be zero or positive")
  private BigDecimal unitPrice;

  @DecimalMin(value = "0.00", message = "Discount must be zero or positive")
  private BigDecimal discount;

  @DecimalMin(value = "0.00", message = "Tax rate must be zero or positive")
  private BigDecimal taxRate;
}
