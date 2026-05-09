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

@Slf4j
@Component
public class TenantContextFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      // Always clear TenantContext to prevent leaks between requests
      TenantContext.clear();
      org.slf4j.MDC.remove("tenantId");
    }
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.contains(".")
        || path.startsWith("/actuator/")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/api-docs");
  }
}
