package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // If context already set (by JwtFilter), trust it and skip header check
        if (TenantContext.getTenantId() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only allow header-based tenant identification for public tenant-routing paths
        String path = request.getRequestURI();
        if (!isPublicTenantRoute(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String tenantHeader = request.getHeader("X-Tenant-ID");
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                TenantContext.setTenantId(Long.parseLong(tenantHeader));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isPublicTenantRoute(String path) {
        return path.startsWith("/api/auth/signup") 
            || path.startsWith("/api/platform/invites/complete");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/actuator/health".equals(path)
            || path.equals("/api/v1/actuator/health")
            || path.startsWith("/auth/")
            || path.startsWith("/platform/auth/");
    }
}
