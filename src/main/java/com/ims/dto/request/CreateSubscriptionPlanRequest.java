package com.ims.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

@Data
public class CreateSubscriptionPlanRequest {

  @NotBlank(message = "Plan name is required")
  private String name;

  @NotNull(message = "Price is required")
  @Min(value = 0, message = "Price must be >= 0")
  private BigDecimal price;

  private String currency = "INR";

  @NotBlank(message = "Billing cycle is required")
  private String billingCycle;

  private Map<String, Object> features;

  @Min(value = 0, message = "Max users must be >= 0")
  private Integer maxUsers = 0;

  @Min(value = 0, message = "Max products must be >= 0")
  private Integer maxProducts = 0;
}
