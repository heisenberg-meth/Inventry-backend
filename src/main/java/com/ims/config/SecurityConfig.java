package com.ims.config;

import com.ims.shared.audit.TraceFilter;
import com.ims.shared.auth.JwtFilter;
import com.ims.shared.auth.NaasAccessDeniedHandler;
import com.ims.shared.auth.NaasAuthenticationEntryPoint;
import com.ims.shared.auth.TenantFilter;
import com.ims.shared.ratelimit.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final TraceFilter traceFilter;
  private final TenantFilter tenantFilter;
  private final NaasAuthenticationEntryPoint naasAuthenticationEntryPoint;
  private final NaasAccessDeniedHandler naasAccessDeniedHandler;

  @Value("${app.security.allowed-origins:*}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers
            .frameOptions(frame -> frame.deny())
            .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
            .referrerPolicy(referrer -> referrer.policy(
                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .xssProtection(xss -> xss.headerValue(
                org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentSecurityPolicy(
                csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none';"))
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000).preload(true)))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(naasAuthenticationEntryPoint)
                .accessDeniedHandler(naasAccessDeniedHandler))
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(
                "/api/auth/login",
                "/api/auth/signup",
                "/api/auth/forgot-password",
                "/api/auth/reset-password",
                "/api/auth/verify-email",
                "/api/auth/resend-verification",
                "/api/auth/check-email",
                "/api/auth/check-slug",
                "/api/auth/check-company-code",
                "/api/platform/auth/**")
                .permitAll()
                .requestMatchers("/api/auth/logout", "/api/auth/me", "/api/auth/change-password",
                    "/api/auth/permissions", "/api/auth/validate")
                .authenticated()
                .requestMatchers("/api/platform/invites/accept", "/api/platform/invites/complete")
                .permitAll()
                .requestMatchers("/api/tenant/payments/gateway/webhook")
                .permitAll()
                .requestMatchers("/actuator/health", "/actuator/info")
                .permitAll()
                .requestMatchers("/actuator/**")
                .hasRole("ADMIN")
                .requestMatchers(
                    "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html", "/v3/api-docs/**")
                .authenticated()
                .anyRequest()
                .authenticated())
        .addFilterBefore(traceFilter, org.springframework.security.web.context.SecurityContextHolderFilter.class)
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(tenantFilter, jwtFilter.getClass());

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    if (allowedOrigins == null || allowedOrigins.trim().isEmpty() || "*".equals(allowedOrigins)) {
      configuration.setAllowedOrigins(List.of("*"));
      configuration.setAllowCredentials(false);
    } else {
      configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
      configuration.setAllowCredentials(true);
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration
        .setAllowedHeaders(
            List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Tenant-ID", "ngrok-skip-browser-warning"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}