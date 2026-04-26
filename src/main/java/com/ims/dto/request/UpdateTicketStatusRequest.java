package com.ims.dto.request;

import com.ims.model.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateTicketStatusRequest {

  @NotNull(message = "Status is required")
  private SupportTicketStatus status;

  private String reason;
}
