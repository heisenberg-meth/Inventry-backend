package com.ims.tenant.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.lang.Nullable;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = false)
public class OrderItemRequest {
  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Quantity is required")
  @Min(value = 1, message = "Quantity must be at least 1")
  private Integer quantity;

  @NotNull(message = "Unit price is required")
  @DecimalMin(value = "0.0", message = "Unit price cannot be negative")
  private BigDecimal unitPrice;

  @Nullable
  private BigDecimal discount;
  @Nullable
  private BigDecimal taxRate;

  @AssertTrue(message = "Discount cannot exceed unit price")
  public boolean isValidDiscount() {
      return discount == null || unitPrice == null || discount.compareTo(unitPrice) <= 0;
  }
}
