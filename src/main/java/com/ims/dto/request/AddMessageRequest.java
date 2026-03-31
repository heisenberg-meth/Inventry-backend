package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddMessageRequest {

  @NotBlank(message = "Message is required")
  private String message;
}
