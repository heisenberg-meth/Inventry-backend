package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that handles tenant context resolution from headers for public routes.
 * Authoritative tenant resolution for authenticated routes is handled by
 * JwtFilter.
 */
@Slf4j
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        try {
            String tenantHeader = request.getHeader(TENANT_HEADER);
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                try {
                    Long tenantId = Long.parseLong(tenantHeader);
                    TenantContext.setTenantId(tenantId);
                    MDC.put("tenantId", String.valueOf(tenantId));
                    log.debug("Tenant context set from header: {}", tenantId);
                } catch (NumberFormatException e) {
                    log.warn("Invalid tenant ID in header: {}", tenantHeader);
                }
            }
            chain.doFilter(request, response);
        } finally {
            // Always clear TenantContext to prevent leaks between requests
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();

        // 1. Skip static resources and internal actuator endpoints
        if (path.contains(".")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")) {
            return true;
        }

        // 2. ONLY apply for specific public routes that legitimately need header-based
        // tenant resolution.
        // For all other routes (authenticated), JwtFilter will set the authoritative
        // tenant ID.
        // If we don't have specific public multi-tenant routes yet, we can skip
        // filtering for all
        // EXCEPT those starting with specific prefixes if needed.

        // As per code review recommendation, we only apply this for truly public
        // multi-tenant routes.
        // Example: return !path.startsWith("/api/public/tenant-aware-endpoint");

        // For now, following the specific suggestion:
        return !path.startsWith("/api/platform/invites/");
    }
}
