package com.ims.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private static final int AUTH_RPM = 5;
  private static final int PUBLIC_RPM = 10;
  private static final int AUTHENTICATED_RPM = 15;
  private static final int TENANT_RPM = 20;
  private static final int WINDOW_SECONDS = 60;

  private RateLimiterService rateLimiterService;

  private final JwtUtil jwtUtil = mock(JwtUtil.class);

  private RateLimitFilter filter;

  @BeforeEach
  void setup() {
    rateLimiterService = mock(RateLimiterService.class);
    filter = new RateLimitFilter(
        rateLimiterService,
        jwtUtil,
        true,
        AUTH_RPM,
        PUBLIC_RPM,
        AUTHENTICATED_RPM,
        TENANT_RPM,
        WINDOW_SECONDS);
  }

  @Test
  void allowsRequestBelowPublicLimit() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);
    when(rateLimiterService.getCount(anyString())).thenReturn(1);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/products");
    req.setRemoteAddr("10.0.0.1");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    assertEquals(String.valueOf(PUBLIC_RPM), res.getHeader("X-RateLimit-Limit"));
    assertEquals(String.valueOf(PUBLIC_RPM - 1), res.getHeader("X-RateLimit-Remaining"));
    assertEquals(String.valueOf(WINDOW_SECONDS), res.getHeader("X-RateLimit-Window-Seconds"));
    verify(chain).doFilter(req, res);
  }

  @Test
  void blocksRequestOverPublicLimit() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(false);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/something");
    req.setRemoteAddr("10.0.0.2");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(429, res.getStatus());
    assertEquals(String.valueOf(WINDOW_SECONDS), res.getHeader("Retry-After"));
    assertEquals("application/json", res.getContentType());
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void enforcesStricterLimitOnAuthEndpoints() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), eq(AUTH_RPM), anyInt(), anyBoolean())).thenReturn(false);

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
    req.setRemoteAddr("10.0.0.3");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(429, res.getStatus());
    assertEquals(String.valueOf(AUTH_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void usesTenantLimitWhenBearerTokenCarriesTenantId() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);
    when(jwtUtil.extractUserId("good-token")).thenReturn(123L);
    when(jwtUtil.extractTenantId("good-token")).thenReturn(42L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/products");
    req.setRemoteAddr("10.0.0.4");
    req.addHeader("Authorization", "Bearer good-token");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    // Primary limit for authenticated user
    assertEquals(String.valueOf(AUTHENTICATED_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(rateLimiterService).isAllowed(eq("rate_limit:user:123"), eq(AUTHENTICATED_RPM), eq(WINDOW_SECONDS),
        eq(false));
    verify(rateLimiterService).isAllowed(eq("rate_limit:tenant:42"), eq(TENANT_RPM), eq(WINDOW_SECONDS), eq(false));
    verify(chain).doFilter(req, res);
  }

  @Test
  void fallsBackToPublicTierWhenTokenIsInvalid() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);
    when(jwtUtil.extractUserId("bad-token")).thenThrow(new RuntimeException("bad token"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/products");
    req.setRemoteAddr("10.0.0.5");
    req.addHeader("Authorization", "Bearer bad-token");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    assertEquals(String.valueOf(PUBLIC_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(rateLimiterService).isAllowed(eq("rate_limit:ip:10.0.0.5"), eq(PUBLIC_RPM), eq(WINDOW_SECONDS));
    verify(chain).doFilter(req, res);
  }

  @Test
  void honorsXForwardedForWhenBuildingKey() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99"); // proxy
    req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.99");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(rateLimiterService).isAllowed(eq("rate_limit:ip:203.0.113.7"), anyInt(), anyInt());
  }

  @Test
  void usesSingleXForwardedForIpWhenPresent() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99");
    req.addHeader("X-Forwarded-For", "198.51.100.23");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(rateLimiterService).isAllowed(eq("rate_limit:ip:198.51.100.23"), anyInt(), anyInt());
  }

  @Test
  void fallsBackToXRealIpWhenForwardedForBlank() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99");
    req.addHeader("X-Forwarded-For", "   ");
    req.addHeader("X-Real-IP", "198.51.100.42");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(rateLimiterService).isAllowed(eq("rate_limit:ip:198.51.100.42"), anyInt(), anyInt());
  }

  @Test
  void fallsBackToRemoteAddrWhenNoProxyHeaders() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("192.0.2.55");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(rateLimiterService).isAllowed(eq("rate_limit:ip:192.0.2.55"), anyInt(), anyInt(), anyBoolean());
  }

  @Test
  void failsOpenWhenRedisThrows() throws Exception {
    // Note: In real life, the circuit breaker in RateLimiterService would catch
    // this.
    // Here we mock the service to throw directly.
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), eq(false)))
        .thenThrow(new RuntimeException("Redis down"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.6");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    // We expect the filter to catch the exception and allow the request (fail-open)
    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    verify(chain).doFilter(req, res);
  }

  @Test
  void failsClosedWhenRedisThrowsOnAuthEndpoint() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), eq(true)))
        .thenThrow(new RuntimeException("Redis down"));

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
    req.setRemoteAddr("10.0.0.6");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    // For auth endpoints, we expect it to fail closed (block request)
    filter.doFilter(req, res, chain);

    assertEquals(429, res.getStatus());
    verify(chain, never()).doFilter(any(), any());
  }

  @Test
  void skipsExcludedActuatorPath() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
    req.setRemoteAddr("10.0.0.7");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertNull(res.getHeader("X-RateLimit-Limit"));
    verify(chain).doFilter(req, res);
    verify(rateLimiterService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
  }

  @Test
  void skipsExcludedSwaggerAndApiDocsPaths() throws Exception {
    for (String path : new String[] { "/swagger-ui/index.html", "/v3/api-docs/ims-api" }) {
      MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
      req.setRemoteAddr("10.0.0.8");
      MockHttpServletResponse res = new MockHttpServletResponse();
      FilterChain chain = mock(FilterChain.class);

      filter.doFilter(req, res, chain);

      assertNull(res.getHeader("X-RateLimit-Limit"), "headers set for " + path);
      verify(chain).doFilter(req, res);
    }
    verify(rateLimiterService, never()).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
  }

  @Test
  void stillRateLimitsNearMissOfExcludedPath() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuatorx/health");
    req.setRemoteAddr("10.0.0.9");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertNotNull(res.getHeader("X-RateLimit-Limit"));
    verify(chain).doFilter(req, res);
    verify(rateLimiterService).isAllowed(anyString(), anyInt(), anyInt(), anyBoolean());
  }

  @Test
  void doesNotTreatUnrelatedAuthSubstringAsAuthEndpoint() throws Exception {
    when(rateLimiterService.isAllowed(anyString(), anyInt(), anyInt(), anyBoolean())).thenReturn(true);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/auth-logs");
    req.setRemoteAddr("10.0.0.10");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    // Not the auth tier — contains "/auth" as a substring but is not /auth or
    // /api/auth subtree.
    assertEquals(String.valueOf(PUBLIC_RPM), res.getHeader("X-RateLimit-Limit"));
  }

  @Test
  void rejectsInvalidConfiguration() {
    // Each setting is checked and reported independently so operators see the exact
    // property key
    // (e.g. app.rate-limit.public-rpm) that needs fixing.
    IllegalArgumentException authEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            rateLimiterService, jwtUtil, true, 0, PUBLIC_RPM, AUTHENTICATED_RPM, TENANT_RPM, WINDOW_SECONDS));
    assertEquals("app.rate-limit.auth-rpm must be >= 1 (got 0)", authEx.getMessage());

    IllegalArgumentException publicEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            rateLimiterService, jwtUtil, true, AUTH_RPM, 0, AUTHENTICATED_RPM, TENANT_RPM, WINDOW_SECONDS));
    assertEquals("app.rate-limit.public-rpm must be >= 1 (got 0)", publicEx.getMessage());

    IllegalArgumentException authenticatedEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            rateLimiterService, jwtUtil, true, AUTH_RPM, PUBLIC_RPM, 0, TENANT_RPM, WINDOW_SECONDS));
    assertEquals(
        "app.rate-limit.authenticated-rpm must be >= 1 (got 0)", authenticatedEx.getMessage());

    IllegalArgumentException tenantEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            rateLimiterService, jwtUtil, true, AUTH_RPM, PUBLIC_RPM, AUTHENTICATED_RPM, 0, WINDOW_SECONDS));
    assertEquals(
        "app.rate-limit.tenant-rpm must be >= 1 (got 0)", tenantEx.getMessage());

    IllegalArgumentException windowEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(rateLimiterService, jwtUtil, true, AUTH_RPM, PUBLIC_RPM, AUTHENTICATED_RPM,
            TENANT_RPM, 0));
    assertEquals(
        "app.rate-limit.window-seconds must be >= 1 (got 0)", windowEx.getMessage());
  }
}
