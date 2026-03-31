package com.ims.dto.request;

import java.math.BigDecimal;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateSubscriptionPlanRequest {

  private String name;

  private BigDecimal price;

  private Map<String, Object> features;

  private Integer maxUsers;

  private Integer maxProducts;
}
