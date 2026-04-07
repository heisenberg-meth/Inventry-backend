package com.ims.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

  @NotBlank(message = "Name is required")
  private String name;

  private String description;

  @JsonProperty("tax_rate")
  private java.math.BigDecimal taxRate;
}
