package com.ims.shared.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

  private final Long userId;
  private final Long tenantId;
  private final String principal;

  public JwtAuthenticationToken(
      String principal,
      Long userId,
      Long tenantId,
      Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.userId = userId;
    this.tenantId = tenantId;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return "";
  }

  @Override
  public Object getPrincipal() {
    return principal;
  }

  public Long getUserId() {
    return userId;
  }

  public Long getTenantId() {
    return tenantId;
  }
}
