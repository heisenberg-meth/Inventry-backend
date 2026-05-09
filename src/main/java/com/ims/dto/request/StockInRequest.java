package com.ims.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StockInRequest {
  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private Integer quantity;

  @Size(max = 500, message = "Notes must not exceed 500 characters")
  private String notes;
}
