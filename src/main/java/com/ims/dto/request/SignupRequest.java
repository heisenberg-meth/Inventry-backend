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
  @Size(min = 6, max = 100)
  private String password;

  @Size(max = 20, message = "Phone must not exceed 20 characters")
  private String ownerPhone;

  @Size(max = 500, message = "Address must not exceed 500 characters")
  private String address;

  @Size(max = 15, message = "GSTIN must not exceed 15 characters")
  private String gstin;

  @Size(max = 100, message = "Workspace slug must not exceed 100 characters")
  private String workspaceSlug;
}
