package com.ims.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  private static final int AUTH_RPM = 5;
  private static final int PUBLIC_RPM = 10;
  private static final int TENANT_RPM = 20;
  private static final int WINDOW_SECONDS = 60;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private ZSetOperations<String, Object> zSet;

  @Mock
  private JwtUtil jwtUtil;

  private RateLimitFilter filter;

  @BeforeEach
  void setup() {
    // Lenient: a few tests (excluded paths, constructor-validation) never hit the
    // Redis path.
    lenient().when(redisTemplate.opsForZSet()).thenReturn(zSet);
    filter = new RateLimitFilter(
        redisTemplate,
        jwtUtil,
        AUTH_RPM,
        PUBLIC_RPM,
        TENANT_RPM,
        WINDOW_SECONDS,
        List.of("127.0.0.1", "10.0.0.0/8"));
  }

  @Test
  void allowsRequestBelowPublicLimit() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenant/products");
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
    when(zSet.zCard(anyString())).thenReturn((long) (PUBLIC_RPM + 1));

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
    when(zSet.zCard(anyString())).thenReturn((long) (AUTH_RPM + 1));

    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
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
    when(zSet.zCard(anyString())).thenReturn(1L);
    when(jwtUtil.extractTenantId("good-token")).thenReturn(42L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenant/products");
    req.setRemoteAddr("10.0.0.4");
    req.addHeader("Authorization", "Bearer good-token");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    assertEquals(String.valueOf(TENANT_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(zSet).add(eq("rate:tenant:42:10.0.0.4"), any(), anyDouble());
    verify(redisTemplate)
        .expire(eq("rate:tenant:42:10.0.0.4"), eq((long) WINDOW_SECONDS), any(TimeUnit.class));
    verify(chain).doFilter(req, res);
  }

  @Test
  void fallsBackToPublicTierWhenTokenIsInvalid() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);
    when(jwtUtil.extractTenantId("bad-token")).thenThrow(new RuntimeException("bad token"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenant/products");
    req.setRemoteAddr("10.0.0.5");
    req.addHeader("Authorization", "Bearer bad-token");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    assertEquals(String.valueOf(PUBLIC_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(chain).doFilter(req, res);
  }

  @Test
  void honorsXForwardedForWhenBuildingKey() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99"); // proxy
    req.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.99");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(zSet).add(eq("rate:public:203.0.113.7"), any(), anyDouble());
  }

  @Test
  void usesSingleXForwardedForIpWhenPresent() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99");
    req.addHeader("X-Forwarded-For", "198.51.100.23");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(zSet).add(eq("rate:public:198.51.100.23"), any(), anyDouble());
  }

  @Test
  void fallsBackToXRealIpWhenForwardedForBlank() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.99");
    req.addHeader("X-Forwarded-For", "   ");
    req.addHeader("X-Real-IP", "198.51.100.42");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(zSet).add(eq("rate:public:198.51.100.42"), any(), anyDouble());
  }

  @Test
  void fallsBackToRemoteAddrWhenNoProxyHeaders() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("192.0.2.55");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(zSet).add(eq("rate:public:192.0.2.55"), any(), anyDouble());
  }

  @Test
  void ignoresXForwardedForFromUntrustedProxy() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("203.0.113.1"); // untrusted attacker IP
    req.addHeader("X-Forwarded-For", "1.2.3.4");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    // Should use the untrusted remote address, NOT the spoofed XFF
    verify(zSet).add(eq("rate:public:203.0.113.1"), any(), anyDouble());
  }

  @Test
  void honorsXForwardedForFromTrustedCidrProxy() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.50"); // trusted by 10.0.0.0/8
    req.addHeader("X-Forwarded-For", "1.2.3.4");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    verify(zSet).add(eq("rate:public:1.2.3.4"), any(), anyDouble());
  }

  @Test
  void failsOpenWhenRedisThrows() throws Exception {
    when(zSet.zCard(anyString())).thenThrow(new RuntimeException("redis down"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.6");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    // Cache outage → rate-limit headers are intentionally absent so downstream code
    // cannot
    // mistake them for an authoritative count. Contract locked in by this
    // assertion.
    assertNull(res.getHeader("X-RateLimit-Limit"));
    assertNull(res.getHeader("X-RateLimit-Remaining"));
    assertNull(res.getHeader("Retry-After"));
    verify(chain).doFilter(req, res);
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
    verify(zSet, never()).zCard(anyString());
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
    verify(zSet, never()).zCard(anyString());
  }

  @Test
  void stillRateLimitsNearMissOfExcludedPath() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuatorx/health");
    req.setRemoteAddr("10.0.0.9");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertNotNull(res.getHeader("X-RateLimit-Limit"));
    verify(chain).doFilter(any(), any());
    verify(zSet).zCard(anyString());
  }

  @Test
  void doesNotTreatUnrelatedAuthSubstringAsAuthEndpoint() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenant/auth-logs");
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
            redisTemplate, jwtUtil, 0, PUBLIC_RPM, TENANT_RPM, WINDOW_SECONDS, List.of()));
    assertEquals("app.rate-limit.auth-rpm must be >= 1 (got 0)", authEx.getMessage());

    IllegalArgumentException publicEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            redisTemplate, jwtUtil, AUTH_RPM, 0, TENANT_RPM, WINDOW_SECONDS, List.of()));
    assertEquals("app.rate-limit.public-rpm must be >= 1 (got 0)", publicEx.getMessage());

    IllegalArgumentException tenantEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            redisTemplate, jwtUtil, AUTH_RPM, PUBLIC_RPM, 0, WINDOW_SECONDS, List.of()));
    assertEquals("app.rate-limit.authenticated-rpm must be >= 1 (got 0)", tenantEx.getMessage());

    IllegalArgumentException windowEx = assertThrows(
        IllegalArgumentException.class,
        () -> new RateLimitFilter(
            redisTemplate, jwtUtil, AUTH_RPM, PUBLIC_RPM, TENANT_RPM, 0, List.of()));
    assertEquals("app.rate-limit.window-seconds must be >= 1 (got 0)", windowEx.getMessage());
  }
}
