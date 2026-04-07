package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTicketStatusRequest {

  @NotBlank(message = "Status is required")
  private String status;

  private String reason;
}
