package com.ims.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InvoiceStatusRequest {
  @NotBlank(message = "Status is required")
  private String status;

  private LocalDateTime paidAt;

  private String paymentNotes;
}
