package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

  @NotBlank(message = "Email is required")
  @jakarta.validation.constraints.Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "Reset token is required")
  private String resetToken;

  @NotBlank(message = "New password is required")
  @Size(min = 6, message = "New password must be at least 6 characters")
  private String newPassword;
}
