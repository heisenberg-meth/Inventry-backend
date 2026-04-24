package com.ims.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerResponse {
  private Long id;
  private String name;
  private String phone;
  private String email;
  private String address;
  private String gstin;
}
