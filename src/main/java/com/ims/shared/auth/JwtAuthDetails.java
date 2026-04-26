package com.ims.shared.auth;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@AllArgsConstructor
public class JwtAuthDetails {
  private Long userId;
  private Long tenantId;
  private String role;
  private String scope;
  @Nullable
  private String businessType;
  private boolean isPlatformUser;
  private Set<String> permissions;
  private boolean impersonation;
  @Nullable
  private Long impersonatedBy;
}
