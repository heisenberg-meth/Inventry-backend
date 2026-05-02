package com.ims.shared.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ims.model.ApiKey;

@Component
@Slf4j
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // If already authenticated by JWT, skip API Key check
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String keyHash = hashKey(apiKey);
        Optional<ApiKey> keyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (keyOpt.isEmpty() || !keyOpt.get().isActive() ||
                (keyOpt.get().getExpiresAt() != null && keyOpt.get().getExpiresAt().isBefore(LocalDateTime.now()))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\": 401, \"error\": \"UNAUTHORIZED\", \"message\": \"Invalid or expired API Key\"}");
            return;
        }

        ApiKey key = keyOpt.get();

        // Set Security Context
        Set<String> permissions = Set.of();
        if (key.getScopes() != null && !key.getScopes().isBlank()) {
            permissions = Arrays.stream(key.getScopes().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
        }

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_API_USER"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "API_KEY_" + key.getId(),
                null,
                authorities);

        auth.setDetails(new JwtAuthDetails(
                null, // No specific user ID
                key.getTenantId(),
                "API_USER",
                "TENANT",
                "API", // Generic business type for API keys
                false,
                permissions,
                false,
                null));

        TenantContext.setTenantId(key.getTenantId());
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String hashKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to hash API key", e);
            throw new RuntimeException("Security error");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/signup");
    }
}
