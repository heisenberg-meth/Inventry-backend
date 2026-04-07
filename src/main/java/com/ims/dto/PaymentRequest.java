package com.ims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PaymentRequest {
  @NotNull(message = "Invoice ID is required")
  private Long invoiceId;

  @NotNull(message = "Amount is required")
  @Positive(message = "Amount must be positive")
  private BigDecimal amount;

  private String paymentMode;
  private String reference;
  private String notes;
  private Long userId;
}
