package com.ims.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TransferOrderStatusRequest {
  @NotBlank(message = "Status is required")
  private String status;
}
