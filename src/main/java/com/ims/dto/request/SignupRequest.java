package com.ims.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {
  @NotBlank(message = "Business name is required")
  @Size(max = 255)
  private String businessName;

  @NotBlank(message = "Business type is required")
  @jakarta.validation.constraints.Pattern(regexp = "^(RETAIL|WHOLESALE|MANUFACTURING|SERVICES)$", message = "Business type must be one of: RETAIL, WHOLESALE, MANUFACTURING, SERVICES")
  @Size(max = 50)
  private String businessType;

  @NotBlank(message = "Owner name is required")
  @Size(max = 255)
  private String ownerName;

  @NotBlank(message = "Owner email is required")
  @Email(message = "Invalid email format")
  @Size(max = 255)
  private String ownerEmail;

  @NotBlank(message = "Password is required")
  @Size(min = 10, max = 100)
  @jakarta.validation.constraints.Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=]).{10,}$", message = "Password must contain at least one uppercase, lowercase, digit, and special character")
  private String password;

  private String ownerPhone;

  private String address;
  private String gstin;
  private String workspaceSlug;

  private String idempotencyKey;
}
