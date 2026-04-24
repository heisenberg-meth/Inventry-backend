package com.ims.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerRequest {
  @NotBlank(message = "Name is required")
  private String name;

  private String phone;
  private String email;
  private String address;
  private String gstin;
}
