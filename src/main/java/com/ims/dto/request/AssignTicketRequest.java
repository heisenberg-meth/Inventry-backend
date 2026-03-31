package com.ims.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignTicketRequest {

  @NotNull(message = "Support admin ID is required")
  private Long supportAdminId;
}
