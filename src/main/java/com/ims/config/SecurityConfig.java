package com.ims.config;

import com.ims.shared.audit.TraceFilter;
import com.ims.shared.auth.JwtFilter;
import com.ims.shared.ratelimit.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.http.HttpStatus;


import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final TraceFilter traceFilter;

  @Value("${app.security.allowed-origins:*}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers
            .frameOptions(frame -> frame.deny())
            .xssProtection(xss -> xss.headerValue(org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'none'; object-src 'none';"))
            .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
        )
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/signup", 
                                    "/api/auth/forgot-password", "/api/auth/reset-password", 
                                    "/api/auth/verify-email", "/api/auth/resend-verification",
                                    "/api/auth/check-email", "/api/auth/check-slug", "/api/auth/check-company-code")
                    .permitAll()
                    .requestMatchers("/api/platform/auth/**")
                    .permitAll()
                    .requestMatchers("/api/platform/invites/accept", "/api/platform/invites/complete")
                    .permitAll()
                    .requestMatchers("/api/tenant/payments/gateway/webhook")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui/**", "/api-docs/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(traceFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
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
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID", "ngrok-skip-browser-warning"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
