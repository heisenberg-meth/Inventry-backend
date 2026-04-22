package com.ims.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateWebhookRequest {

  @NotBlank(message = "URL is required")
  @URL(protocol = "http", regexp = "^(http|https)://.*$", message = "Invalid URL format. Only http/https allowed")
  private String url;

  @NotBlank(message = "Event types are required")
  private String eventTypes;

  private String secret;
}
