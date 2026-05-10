package com.ims.helper;

import com.ims.model.User;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityTestUtils {

  public static void setAuthenticatedUser(User user) {
    java.util.List<org.springframework.security.core.GrantedAuthority> authorities =
        new java.util.ArrayList<>();
    if (user.getRole() != null) {
      authorities.add(
          new org.springframework.security.core.authority.SimpleGrantedAuthority(
              "ROLE_" + user.getRole()));
      authorities.add(
          new org.springframework.security.core.authority.SimpleGrantedAuthority(user.getRole()));
    }
    if (user.getCustomPermissions() != null) {
      user.getCustomPermissions()
          .forEach(
              p ->
                  authorities.add(
                      new org.springframework.security.core.authority.SimpleGrantedAuthority(
                          p.getKey())));
    }

    JwtAuthenticationToken auth =
        new JwtAuthenticationToken(user.getEmail(), user.getId(), user.getTenantId(), authorities);

    JwtAuthDetails details =
        new JwtAuthDetails(
            user.getId(),
            user.getTenantId(),
            user.getRole(),
            user.getScope(),
            "PHARMACY", // Updated to match ProcurementIntegrationTest requirements
            user.getIsPlatformUser() != null && user.getIsPlatformUser());
    auth.setDetails(details);
    auth.setAuthenticated(true);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  public static void clear() {
    SecurityContextHolder.clearContext();
  }
}
