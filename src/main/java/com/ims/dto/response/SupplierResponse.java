package com.ims.dto.response;

import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@Builder
public class SupplierResponse {
  @Nullable private Long id;
  private String name;
  @Nullable private String phone;
  @Nullable private String email;
  @Nullable private String address;
  @Nullable private String gstin;
}
