package com.ims.config;

import com.ims.shared.auth.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }

  @Bean
  public OncePerRequestFilter testAuthFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
          FilterChain chain) throws java.io.IOException, jakarta.servlet.ServletException {

        String tenantHeader = request.getHeader("X-Tenant-ID");
        Long tenantId = tenantHeader != null ? Long.parseLong(tenantHeader) : 4L;

        if (request.getRequestURI().contains("/platform/")) {
          tenantId = 1L;
        }

        TenantContext.setTenantId(tenantId);

        // Set default ROOT authentication for tests
        com.ims.shared.auth.JwtAuthDetails details =
            new com.ims.shared.auth.JwtAuthDetails(
                1L, tenantId, "ROOT", "PLATFORM", "SYSTEM", false,
                java.util.Collections.emptySet(), false, null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            1L, details, java.util.List.of(new SimpleGrantedAuthority("ROLE_ROOT")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);
      }
    };
  }
}
