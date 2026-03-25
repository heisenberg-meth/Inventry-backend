package com.ims.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CategoryRequest {

  @NotBlank(message = "Name is required")
  private String name;

  private String description;
}
