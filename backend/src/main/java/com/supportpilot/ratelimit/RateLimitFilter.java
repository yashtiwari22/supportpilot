package com.supportpilot.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.common.TenantContext;
import com.supportpilot.metrics.SupportPilotMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Filter enforcing token bucket rate limits per tenant on ingestion endpoints.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final TenantRateLimiter tenantRateLimiter;
    private final SupportPilotMetrics metrics;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(
            TenantRateLimiter tenantRateLimiter,
            SupportPilotMetrics metrics,
            ObjectMapper objectMapper) {
        this.tenantRateLimiter = tenantRateLimiter;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Rate limit applies to ticket ingestion & creation APIs
        return !(path.equals("/api/v1/tickets") && "POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            // Unauthenticated or tenant not established; let subsequent security filters handle rejection
            filterChain.doFilter(request, response);
            return;
        }

        TenantRateLimiter.RateLimitResult result = tenantRateLimiter.tryAcquire(tenantId);

        // Add standard rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.resetSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            metrics.recordRateLimitRejected(tenantId);

            Map<String, Object> errorPayload = Map.of(
                    "error", "Too Many Requests",
                    "status", 429,
                    "message", "Rate limit exceeded for tenant. Maximum " + result.limit() + " requests per minute allowed.",
                    "retryAfterSeconds", result.resetSeconds()
            );

            objectMapper.writeValue(response.getOutputStream(), errorPayload);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
