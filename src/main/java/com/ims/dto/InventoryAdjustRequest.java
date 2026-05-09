package com.ims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InventoryAdjustRequest {
  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Quantity is required")
  private Integer quantity;

  @Size(max = 500, message = "Notes must not exceed 500 characters")
  private String notes;

  private Long userId;
}
