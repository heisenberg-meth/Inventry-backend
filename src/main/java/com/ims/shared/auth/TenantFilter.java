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

        try {
            String tenantHeader = request.getHeader("X-Tenant-ID");

            if (tenantHeader == null) {
                // If it's a public path, we might allow it, but let's be strict for now as requested
                if (shouldNotFilter(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                log.warn("Missing X-Tenant-ID header for path: {}", request.getRequestURI());
                throw new com.ims.shared.exception.TenantContextException("Missing tenant header");
            }

            TenantContext.setTenantId(Long.parseLong(tenantHeader));
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
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
