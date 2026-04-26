package com.ims.dto;

import com.ims.model.InvoiceStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class InvoiceStatusRequest {
  @NotNull(message = "Status is required")
  private InvoiceStatus status;

  private LocalDateTime paidAt;

  private String paymentNotes;
}
