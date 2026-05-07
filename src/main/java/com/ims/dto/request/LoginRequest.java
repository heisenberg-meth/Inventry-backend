package com.ims.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @NotBlank(message = "Password is required")
  @Size(min = 4, max = 100, message = "Password must be between 4 and 100 characters")
  private String password;

  @Size(max = 100, message = "Company code must not exceed 100 characters")
  private String companyCode;
}
