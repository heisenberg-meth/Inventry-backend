package com.ims.config;

import com.ims.shared.audit.TraceFilter;
import com.ims.shared.auth.JwtFilter;
import com.ims.shared.ratelimit.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /** HSTS max-age: 1 year in seconds (365 * 24 * 60 * 60). */
  private static final int HSTS_MAX_AGE_SECONDS = 31_536_000;

  private final JwtFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final TraceFilter traceFilter;
  private final com.ims.shared.auth.TenantFilter tenantFilter;

  @Value("${app.security.allowed-origins:*}")
  private String allowedOrigins;

  private static final String[] AUTH_WHITELIST = {
    "/api/auth/login",
    "/api/auth/signup",
    "/api/auth/refresh",
    "/api/auth/forgot-password",
    "/api/auth/reset-password",
    "/api/auth/verify-email",
    "/api/auth/resend-verification",
    "/api/auth/check-email",
    "/api/auth/check-slug",
    "/api/auth/check-company-code",
    "/api/platform/auth/login",
    "/api/platform/invites/accept",
    "/api/platform/invites/complete",
    "/api/tenant/payments/gateway/webhook"
  };

  private static final String[] SWAGGER_WHITELIST = {
    "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, org.springframework.core.env.Environment env) throws Exception {
    boolean isDev = java.util.Arrays.asList(env.getActiveProfiles()).contains("dev");

    return configureCommon(http)
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(AUTH_WHITELIST).permitAll();
              auth.requestMatchers("/actuator/health").permitAll();

              if (isDev) {
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers(SWAGGER_WHITELIST).permitAll();
              } else {
                auth.requestMatchers("/actuator/**").hasRole("ROOT");
                auth.requestMatchers(SWAGGER_WHITELIST).hasRole("ROOT");
              }

              auth.requestMatchers("/internal/**").hasRole("ROOT");
              auth.anyRequest().authenticated();
            })
        .build();
  }

  private HttpSecurity configureCommon(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    .frameOptions(frame -> frame.deny())
                    .xssProtection(
                        xss ->
                            xss.headerValue(
                                org.springframework.security.web.header.writers
                                    .XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; object-src 'none';"))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(traceFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(tenantFilter, UsernamePasswordAuthenticationFilter.class);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    if (allowedOrigins == null || "*".equals(allowedOrigins)) {
      // Never use wildcard in production to avoid security risks.
      // Defailing to local dev for safety if nothing provided.
      configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
    } else {
      configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowCredentials(true);
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Correlation-ID",
            "X-Tenant-ID",
            "ngrok-skip-browser-warning"));
    configuration.setExposedHeaders(
        List.of("X-Correlation-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
