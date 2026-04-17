package com.ims.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ims.shared.auth.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private static final int AUTH_RPM = 5;
  private static final int PUBLIC_RPM = 10;
  private static final int TENANT_RPM = 20;
  private static final int WINDOW_SECONDS = 60;

  @SuppressWarnings("unchecked")
  private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ZSetOperations<String, Object> zSet = mock(ZSetOperations.class);

  private final JwtUtil jwtUtil = mock(JwtUtil.class);

  private RateLimitFilter filter;

  @BeforeEach
  void setup() {
    when(redisTemplate.opsForZSet()).thenReturn(zSet);
    filter =
        new RateLimitFilter(
            redisTemplate, jwtUtil, AUTH_RPM, PUBLIC_RPM, TENANT_RPM, WINDOW_SECONDS);
  }

  @Test
  void allowsRequestBelowPublicLimit() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);

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
    when(zSet.zCard(anyString())).thenReturn(1L);
    when(jwtUtil.extractTenantId("good-token")).thenReturn(42L);

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/products");
    req.setRemoteAddr("10.0.0.4");
    req.addHeader("Authorization", "Bearer good-token");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    assertEquals(String.valueOf(TENANT_RPM), res.getHeader("X-RateLimit-Limit"));
    verify(zSet).add(eq("rate:tenant:42:10.0.0.4"), any(), anyDouble());
    verify(chain).doFilter(req, res);
  }

  @Test
  void fallsBackToPublicTierWhenTokenIsInvalid() throws Exception {
    when(zSet.zCard(anyString())).thenReturn(1L);
    when(jwtUtil.extractTenantId("bad-token")).thenThrow(new RuntimeException("bad token"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/tenant/products");
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
  void failsOpenWhenRedisThrows() throws Exception {
    when(zSet.zCard(anyString())).thenThrow(new RuntimeException("redis down"));

    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/public/ping");
    req.setRemoteAddr("10.0.0.6");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertEquals(200, res.getStatus());
    verify(chain).doFilter(req, res);
  }

  @Test
  void skipsExcludedPaths() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
    req.setRemoteAddr("10.0.0.7");
    MockHttpServletResponse res = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(req, res, chain);

    assertNull(res.getHeader("X-RateLimit-Limit"));
    verify(chain).doFilter(req, res);
    verify(zSet, never()).zCard(anyString());
  }
}
