package com.supportpilot.auth;

import com.supportpilot.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * Filter that authenticates ingestion requests via X-API-Key header and establishes TenantContext.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyFilter(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader("X-API-Key");
        if (rawKey != null && !rawKey.isBlank()) {
            String hash = sha256(rawKey.trim());
            Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash);

            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();
                TenantContext.setTenantId(apiKey.getTenantId());

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "api-key:" + apiKey.getPrefix(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_INGESTION"))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"Invalid or revoked API key\"}");
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Clean up TenantContext at the end of the request if set by this filter
            if (rawKey != null) {
                TenantContext.clear();
            }
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
