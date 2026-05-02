package com.ims.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ims.model.User;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.JwtUtil;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

@TestConfiguration
// @EnableWebSecurity removed - conflicts with MockMvcSecurityConfiguration
@EnableAspectJAutoProxy
@Import(TestSecurityBeansConfig.class)
public class TestSecurityConfig {

    private static final String[] AUTH_WHITELIST = {
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/refresh",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification",
            "/api/v1/auth/check-email",
            "/api/v1/auth/check-slug",
            "/api/v1/auth/check-company-code",
            "/api/v1/platform/auth/login",
            "/api/v1/platform/invites/accept",
            "/api/v1/platform/invites/complete",
            "/api/v1/tenant/payments/gateway/webhook"
    };

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/actuator", "/swagger-ui", "/v3/api-docs", "/api-docs",
            "/swagger-resources", "/webjars", "/favicon.ico", "/error");

    private static final List<String> AUTH_PREFIXES = List.of("/auth", "/api/v1/auth");

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
            CompositeTestFilter compositeTestFilter) throws Exception {
        System.out.println("TEST SECURITY FILTER CHAIN CREATED with compositeTestFilter: " + compositeTestFilter);
        return http
                .csrf(csrf -> csrf.disable())
                .anonymous(anon -> anon.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(AUTH_WHITELIST).permitAll();
                    auth.requestMatchers("/actuator/health", "/api/v1/actuator/health").permitAll();
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"message\":\"Unauthorized\",\"status\":401}");
                        }))
                .addFilterBefore(compositeTestFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public CompositeTestFilter compositeTestFilter(UserRepository userRepository,
            TransactionTemplate transactionTemplate,
            RedisTemplate<String, Object> redisTemplate,
            JwtUtil jwtUtil,
            @Value("${app.rate-limit.auth-rpm:20}") int authRpm,
            @Value("${app.rate-limit.public-rpm:100}") int publicRpm,
            @Value("${app.rate-limit.authenticated-rpm:500}") int tenantRpm,
            @Value("${app.rate-limit.window-seconds:60}") int windowSeconds) {
        System.out.println("COMPOSITE FILTER BEAN CREATED");
        return new CompositeTestFilter(userRepository, transactionTemplate, redisTemplate,
                jwtUtil, authRpm, publicRpm, tenantRpm, windowSeconds);
    }

    public static class CompositeTestFilter extends OncePerRequestFilter {
        private final UserRepository userRepository;
        private final TransactionTemplate transactionTemplate;
        private final RedisTemplate<String, Object> redisTemplate;
        private final JwtUtil jwtUtil;
        private final int authRpm;
        private final int publicRpm;
        private final int tenantRpm;
        private final int windowSeconds;
        private final ObjectMapper objectMapper = new ObjectMapper();

        public CompositeTestFilter(UserRepository userRepository,
                TransactionTemplate transactionTemplate,
                RedisTemplate<String, Object> redisTemplate,
                JwtUtil jwtUtil,
                int authRpm, int publicRpm, int tenantRpm, int windowSeconds) {
            this.userRepository = userRepository;
            this.transactionTemplate = transactionTemplate;
            this.redisTemplate = redisTemplate;
            this.jwtUtil = jwtUtil;
            this.authRpm = authRpm;
            this.publicRpm = publicRpm;
            this.tenantRpm = tenantRpm;
            this.windowSeconds = windowSeconds;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws IOException, ServletException {

            String path = request.getRequestURI();
            boolean excluded = EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith);
            if (!excluded) {
                if (!checkRateLimit(request, response, path)) {
                    return;
                }
            }

            Long tenantId = parseTenantId(request);
            String authHeader = request.getHeader("Authorization");

            if (tenantId != null) {
                TenantContext.setTenantId(tenantId);

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    User user = findUser(tenantId);
                    if (user != null) {
                        setAuthentication(user, tenantId);
                    }
                } else {
                    SecurityContextHolder.clearContext();
                }
            }

            try {
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }

        private boolean checkRateLimit(HttpServletRequest req, HttpServletResponse res, String path)
                throws IOException {
            try {
                Long count = redisTemplate.opsForZSet().zCard("test");
                System.out.println("RATE LIMIT CHECK: zCard returned " + count + " for path " + path);
            } catch (Exception e) {
                System.out.println("RATE LIMIT CHECK: Exception: " + e.getMessage());
            }
            boolean isAuthEndpoint = AUTH_PREFIXES.stream().anyMatch(path::contains);

            String tenantId = resolveTenantId(req);
            String clientIp = req.getRemoteAddr();

            int limit;
            String key;
            if (isAuthEndpoint) {
                limit = authRpm;
                key = "rate:auth:" + clientIp;
            } else if (tenantId != null) {
                limit = tenantRpm;
                key = "rate:tenant:" + tenantId + ":" + clientIp;
            } else {
                limit = publicRpm;
                key = "rate:public:" + clientIp;
            }

            long now = System.currentTimeMillis();
            long windowStart = now - (windowSeconds * 1000L);

            try {
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
                redisTemplate.opsForZSet().add(key, String.valueOf(now), now);
                redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

                Long count = redisTemplate.opsForZSet().zCard(key);
                int currentCount = (count != null) ? count.intValue() : 0;

                res.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                res.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
                res.setHeader("X-RateLimit-Window-Seconds", String.valueOf(windowSeconds));

                if (currentCount > limit) {
                    res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    res.setHeader("Retry-After", String.valueOf(windowSeconds));
                    res.setContentType("application/json");
                    res.getWriter().write(objectMapper.writeValueAsString(
                            Map.of("error", "Too Many Requests",
                                    "message", "Rate limit exceeded. Try again later.",
                                    "limit", limit)));
                    return false;
                }
            } catch (Exception e) {
                // Fail open
            }
            return true;
        }

        private Long parseTenantId(HttpServletRequest request) {
            String tenantHeader = request.getHeader("X-Tenant-ID");
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                return Long.parseLong(tenantHeader);
            }
            if (request.getRequestURI().contains("/platform/")) {
                return 1L;
            }
            return null;
        }

        private String resolveTenantId(HttpServletRequest req) {
            String authHeader = req.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return null;
            }
            String token = authHeader.substring("Bearer ".length());
            try {
                Long id = jwtUtil.extractTenantId(token);
                return id == null ? null : id.toString();
            } catch (Exception e) {
                return null;
            }
        }

        private User findUser(Long tenantId) {
            return transactionTemplate.execute(status -> userRepository.findAll().stream()
                    .filter(u -> tenantId.equals(u.getTenantId()))
                    .findFirst()
                    .map(user -> {
                        if (user.getRole() != null) {
                            Hibernate.initialize(user.getRole());
                            Hibernate.initialize(user.getRole().getPermissions());
                        }
                        return user;
                    })
                    .orElse(null));
        }

        private void setAuthentication(User user, Long tenantId) {
            List<SimpleGrantedAuthority> authorities = user.getRole().getPermissions().stream()
                    .map(p -> new SimpleGrantedAuthority("ROLE_" + p.getKey()))
                    .collect(Collectors.toList());
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));

            JwtAuthDetails details = new JwtAuthDetails(
                    user.getId(), tenantId, user.getRole().getName(), user.getScope(), "RETAIL", false,
                    user.getRole().getPermissions().stream().map(p -> p.getKey()).collect(Collectors.toSet()),
                    false, null);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    user.getId(), details, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
}
