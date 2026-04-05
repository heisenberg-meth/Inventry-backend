package com.ims.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
  @NotBlank(message = "Business name is required")
  @Size(max = 255)
  private String businessName;

  @NotBlank(message = "Business type is required")
  @Size(max = 50)
  private String businessType;

  @NotBlank(message = "Workspace slug is required")
  @Pattern(regexp = "^[a-z0-9-]+$", message = "Workspace slug must contain only lowercase letters, numbers, and hyphens")
  @Size(max = 255)
  private String workspaceSlug;

  @NotBlank(message = "Owner name is required")
  @Size(max = 255)
  private String ownerName;

  @NotBlank(message = "Owner email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255)
  private String ownerEmail;

  @NotBlank(message = "Password is required")
  @Size(min = 6, max = 100)
  private String password;
}