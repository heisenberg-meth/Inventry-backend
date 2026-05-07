package com.ims.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequest {

  @NotBlank(message = "Email is required")
  @Email(message = "Invalid email format")
  private String email;

  @Size(max = 100, message = "Company code must not exceed 100 characters")
  private String companyCode;
}
