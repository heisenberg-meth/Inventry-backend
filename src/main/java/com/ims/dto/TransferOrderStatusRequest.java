package com.ims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferOrderStatusRequest {
  @NotBlank(message = "Status is required")
  @Size(max = 20, message = "Status must not exceed 20 characters")
  private String status;
}
