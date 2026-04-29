package com.ims.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

  @NotBlank(message = "Name is required")
  private String name;
  private String description;
  private BigDecimal taxRate;
}
