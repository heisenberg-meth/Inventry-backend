package com.ims.config;

import com.ims.shared.auth.JwtFilter;
import com.ims.shared.audit.TraceFilter;
import com.ims.shared.auth.ApiKeyFilter;
import com.ims.shared.auth.TenantFilter;
import com.ims.shared.ratelimit.RateLimitFilter;
import com.ims.shared.security.IpWhitelistFilter;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAspectJAutoProxy
@Profile("!test")
@RequiredArgsConstructor
public class SecurityConfig {

  private static final int HSTS_MAX_AGE_SECONDS = 31_536_000;

  private final JwtFilter jwtFilter;
  private final RateLimitFilter rateLimitFilter;
  private final TraceFilter traceFilter;
  private final TenantFilter tenantFilter;
  private final IpWhitelistFilter ipWhitelistFilter;
  private final ApiKeyFilter apiKeyFilter;

  @Value("${app.security.allowed-origins:*}")
  private String allowedOrigins;

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

  private static final String[] SWAGGER_WHITELIST = {
      "/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/v3/api-docs/**"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, Environment env) throws Exception {
    boolean isDev = Arrays.asList(env.getActiveProfiles()).contains("dev");

    return configureCommon(http)
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(AUTH_WHITELIST).permitAll();
              auth.requestMatchers("/actuator/health", "/api/v1/actuator/health").permitAll();

              if (isDev) {
                auth.requestMatchers("/actuator/**").permitAll();
                auth.requestMatchers(SWAGGER_WHITELIST).permitAll();
              } else {
                // Production-grade restrictions
                auth.requestMatchers("/actuator/**").hasAuthority("ROLE_ROOT");
                auth.requestMatchers(SWAGGER_WHITELIST).denyAll();
              }

              auth.requestMatchers("/internal/**").hasAuthority("ROLE_ROOT");
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
            headers -> headers
                .frameOptions(frame -> frame.deny())
                .xssProtection(
                    xss -> xss.headerValue(
                        HeaderValue.ENABLED_MODE_BLOCK))
                .contentTypeOptions(
                    Objects.requireNonNull(Customizer.withDefaults()))
                .contentSecurityPolicy(
                    csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none';"))
                .httpStrictTransportSecurity(
                    hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(HSTS_MAX_AGE_SECONDS)))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(traceFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(ipWhitelistFilter, TraceFilter.class)
        .addFilterAfter(rateLimitFilter, TraceFilter.class)
        .addFilterAfter(tenantFilter, RateLimitFilter.class)
        .addFilterAfter(apiKeyFilter, TenantFilter.class)
        .addFilterAfter(jwtFilter, ApiKeyFilter.class);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    if ("*".equals(allowedOrigins)) {
      throw new IllegalStateException("Wildcard '*' allowed-origins is invalid with allow-credentials=true");
    }
    if (allowedOrigins == null || allowedOrigins.isBlank()) {
      configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
    } else {
      configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    }
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowCredentials(true);
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "X-Correlation-ID",
            "X-Tenant-ID"));
    configuration.setExposedHeaders(
        List.of("X-Correlation-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
