package com.ims.shared.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceFilter extends OncePerRequestFilter {

  private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  private static final String REQUEST_ID_HEADER = "X-Request-ID";
  private static final String MDC_CORRELATION_ID = "correlation_id";
  private static final String MDC_REQUEST_ID = "request_id";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String correlationId = request.getHeader(CORRELATION_ID_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = correlationId;
    }

    MDC.put(MDC_CORRELATION_ID, correlationId);
    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(CORRELATION_ID_HEADER, correlationId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    AuditLogService.setCurrentRequestId(requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_CORRELATION_ID);
      MDC.remove(MDC_REQUEST_ID);
    }
  }
}
