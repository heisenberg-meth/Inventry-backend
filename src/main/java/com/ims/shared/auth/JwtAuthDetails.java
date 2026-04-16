package com.ims.shared.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class JwtAuthDetails {
  private Long userId;
  private Long tenantId;
  private String role;
  private String scope;
  private String businessType;
  private boolean isPlatformUser;
}
