package com.ims.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Data;

@Data
public class CreateInvoiceRequest {
  @NotNull(message = "Order ID is required")
  private Long orderId;

  private LocalDate dueDate;

  private String notes;
}
