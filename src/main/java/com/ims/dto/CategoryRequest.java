package com.ims.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
public class CategoryRequest {

  @NotBlank(message = "Name is required")
  private String name;

  @Nullable
  private String description;

  @Nullable
  private BigDecimal taxRate;
}
