package com.ims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateInvoiceRequest {
  @NotNull(message = "Order ID is required")
  private Long orderId;

  private LocalDate dueDate;

  @Size(max = 500, message = "Notes must not exceed 500 characters")
  private String notes;
}
