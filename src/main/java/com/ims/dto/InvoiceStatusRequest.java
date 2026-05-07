package com.ims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InvoiceStatusRequest {
  @NotBlank(message = "Status is required")
  @Size(max = 20, message = "Status must not exceed 20 characters")
  private String status;

  private LocalDateTime paidAt;

  @Size(max = 500, message = "Payment notes must not exceed 500 characters")
  private String paymentNotes;
}
