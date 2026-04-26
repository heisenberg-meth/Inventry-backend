package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
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
        org.slf4j.MDC.put("tenantId", String.valueOf(tenantId));
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      org.slf4j.MDC.remove("tenantId");
    }
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    return "/actuator/health".equals(path) || path.equals("/api/v1/actuator/health");
  }
}
