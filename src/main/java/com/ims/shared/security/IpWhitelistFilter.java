package com.ims.shared.security;

import com.ims.shared.auth.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final TenantSecurityService tenantSecurityService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String whitelist = tenantSecurityService.getCachedIpWhitelist(tenantId);
        if (whitelist == null || whitelist.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        List<String> allowedIps = Arrays.asList(whitelist.split(","));
        boolean allowed = false;
        for (String allowedIp : allowedIps) {
            try {
                IpAddressMatcher matcher = new IpAddressMatcher(allowedIp.trim());
                if (matcher.matches(clientIp)) {
                    allowed = true;
                    break;
                }
            } catch (Exception e) {
                log.error("Invalid IP/CIDR in whitelist for tenant {}: {}", tenantId, allowedIp);
            }
        }

        if (!allowed) {
            log.warn("Blocked request to tenant {} from unauthorized IP: {}", tenantId, clientIp);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\": 403, \"error\": \"FORBIDDEN\", \"message\": \"Access from your IP address is restricted by the workspace administrator.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/public/");
    }
}
