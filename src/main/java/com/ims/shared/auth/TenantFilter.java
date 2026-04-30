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
public class TenantFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    if (TenantContext.getTenantId() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      String tenantHeader = request.getHeader("X-Tenant-ID");
      if (tenantHeader != null && !tenantHeader.isBlank()) {
        Long tenantId = Long.parseLong(tenantHeader);
        TenantContext.setTenantId(tenantId);
        MDC.put("tenantId", String.valueOf(tenantId));
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      MDC.remove("tenantId");
    }
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return "/actuator/health".equals(path) || path.equals("/api/v1/actuator/health");
  }
}
