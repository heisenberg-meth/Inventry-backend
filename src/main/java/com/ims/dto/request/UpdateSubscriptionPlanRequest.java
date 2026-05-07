package com.ims.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateSubscriptionPlanRequest {

  @Size(max = 255, message = "Name must not exceed 255 characters")
  private String name;

  @Positive(message = "Price must be zero or positive")
  private BigDecimal price;

  private Map<String, Object> features;

  @Positive(message = "Max users must be positive")
  private Integer maxUsers;

  @Positive(message = "Max products must be positive")
  private Integer maxProducts;
}
