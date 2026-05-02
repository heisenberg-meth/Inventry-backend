package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.jboss.logging.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    if (shouldNotFilter(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    String tenantHeader = request.getHeader("X-Tenant-ID");
    if (tenantHeader == null || tenantHeader.isBlank()) {
      log.warn("Missing X-Tenant-ID header for request: {}", request.getRequestURI());
      sendError(response, "Missing X-Tenant-ID header", 401);
      return;
    }

    try {
      Long tenantId = Long.parseLong(tenantHeader);
      TenantContext.setTenantId(tenantId);
      MDC.put("tenantId", String.valueOf(tenantId));
      filterChain.doFilter(request, response);
    } catch (NumberFormatException e) {
      log.warn("Invalid X-Tenant-ID header: {}", tenantHeader);
      sendError(response, "Invalid X-Tenant-ID header format", 401);
    } finally {
      TenantContext.clear();
      MDC.remove("tenantId");
    }
  }

  private void sendError(HttpServletResponse response, String message, int status) throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write(String.format(
        "{\"status\":%d,\"error\":\"UNAUTHORIZED\",\"message\":\"%s\",\"path\":\"\"}",
        status, message));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return "/actuator/health".equals(path)
        || path.equals("/api/v1/actuator/health")
        || path.contains("/auth/")
        || path.contains("/platform/auth/");
  }
}
