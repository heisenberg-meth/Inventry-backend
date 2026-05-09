package com.ims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class PaymentRequest {
  @NotNull(message = "Invoice ID is required")
  private Long invoiceId;

  @NotNull(message = "Amount is required")
  @Positive(message = "Amount must be positive")
  private BigDecimal amount;

  @Size(max = 50, message = "Payment mode must not exceed 50 characters")
  private String paymentMode;

  @Size(max = 255, message = "Reference must not exceed 255 characters")
  private String reference;

  @Size(max = 500, message = "Notes must not exceed 500 characters")
  private String notes;

  private Long userId;
}
