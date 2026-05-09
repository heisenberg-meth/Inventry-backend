package com.ims.helper;

import com.ims.model.User;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Collections;

public class SecurityTestUtils {

    public static void setAuthenticatedUser(User user) {
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                user.getEmail(),
                user.getId(),
                user.getTenantId(),
                Collections.emptyList());

        JwtAuthDetails details = new JwtAuthDetails(
                user.getId(),
                user.getTenantId(),
                user.getRole(),
                user.getScope(),
                "RETAIL", // Default
                user.getIsPlatformUser() != null && user.getIsPlatformUser());
        auth.setDetails(details);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
