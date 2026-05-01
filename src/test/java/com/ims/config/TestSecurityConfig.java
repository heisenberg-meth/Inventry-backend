package com.ims.config;

import com.ims.model.User;
import com.ims.shared.auth.JwtAuthDetails;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Test security configuration that replaces the production filter chain.
 * <p>
 * Key differences from production:
 * <ul>
 * <li>No JWT validation — uses a synthetic auth filter that sets up
 * SecurityContext from X-Tenant-ID header + DB lookup</li>
 * <li>Auth whitelist endpoints are still permit-all</li>
 * <li>Everything else still requires authentication (NOT permitAll)</li>
 * </ul>
 */
@TestConfiguration
@EnableWebSecurity
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

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http,
            OncePerRequestFilter testAuthFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(AUTH_WHITELIST).permitAll();
                    auth.requestMatchers("/actuator/health", "/api/v1/actuator/health").permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(testAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public OncePerRequestFilter testAuthFilter(UserRepository userRepository) {
        return new TestAuthFilter(userRepository);
    }

    private static class TestAuthFilter extends OncePerRequestFilter {
        private final UserRepository userRepository;

        public TestAuthFilter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {

            // If SecurityContext already has auth (e.g. from @WithMockUser or setUp()),
            // skip
            if (SecurityContextHolder.getContext().getAuthentication() != null
                    && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
                ensureTenantContext(request);
                chain.doFilter(request, response);
                return;
            }

            Long tenantId = parseTenantId(request);
            if (tenantId == null) {
                // No tenant header and no existing auth — let Spring Security reject as 401
                chain.doFilter(request, response);
                return;
            }

            TenantContext.setTenantId(tenantId);

            try {
                User user = findUser(userRepository, tenantId);
                if (user != null) {
                    setAuthentication(user, tenantId);
                } else {
                    setRootAuthentication(tenantId);
                }
                chain.doFilter(request, response);
            } finally {
                TenantContext.clear();
            }
        }

        private Long parseTenantId(HttpServletRequest request) {
            String tenantHeader = request.getHeader("X-Tenant-ID");
            if (tenantHeader != null && !tenantHeader.isBlank()) {
                return Long.parseLong(tenantHeader);
            }
            // Platform endpoints default to tenant 1
            if (request.getRequestURI().contains("/platform/")) {
                return 1L;
            }
            return null;
        }

        private void ensureTenantContext(HttpServletRequest request) {
            if (TenantContext.getTenantId() == null) {
                Long tenantId = parseTenantId(request);
                if (tenantId != null) {
                    TenantContext.setTenantId(tenantId);
                }
            }
        }

        private User findUser(UserRepository userRepository, Long tenantId) {
            try {
                return userRepository.findAll().stream()
                        .filter(u -> tenantId.equals(u.getTenantId()))
                        .findFirst()
                        .orElse(null);
            } catch (Exception e) {
                return null;
            }
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

        private void setRootAuthentication(Long tenantId) {
            JwtAuthDetails details = new JwtAuthDetails(
                    1L, tenantId, "ROOT", "PLATFORM", "SYSTEM", false,
                    Collections.emptySet(), false, null);

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    1L, details, List.of(new SimpleGrantedAuthority("ROLE_ROOT")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
    }
}