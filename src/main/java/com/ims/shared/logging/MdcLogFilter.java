package com.ims.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/**
 * Filter to add a unique Trace ID and Tenant ID to every request's logging
 * context.
 * This ensures that all logs for a single request can be correlated easily.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLogFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String TRACE_HEADER = "X-Trace-ID";
    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        String tenantId = request.getHeader(TENANT_HEADER);

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            if (tenantId != null) {
                MDC.put(TENANT_ID_KEY, tenantId);
            }

            // Add trace ID to response headers for client-side correlation
            response.setHeader(TRACE_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(TENANT_ID_KEY);
        }
    }
}
