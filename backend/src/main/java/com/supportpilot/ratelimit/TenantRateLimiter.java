package com.supportpilot.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-tenant Token Bucket rate limiter providing per-tenant quota enforcement.
 */
@Component
public class TenantRateLimiter {

    private final int requestsPerMinute;
    private final boolean enabled;
    private final Map<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();

    public record RateLimitResult(
            boolean allowed,
            long limit,
            long remaining,
            long resetSeconds
    ) {}

    public TenantRateLimiter(
            @Value("${supportpilot.ratelimit.requests-per-minute:60}") int requestsPerMinute,
            @Value("${supportpilot.ratelimit.enabled:true}") boolean enabled) {
        this.requestsPerMinute = requestsPerMinute;
        this.enabled = enabled;
    }

    public RateLimitResult tryAcquire(UUID tenantId) {
        if (!enabled || tenantId == null) {
            return new RateLimitResult(true, requestsPerMinute, requestsPerMinute, 0);
        }

        TokenBucket bucket = buckets.computeIfAbsent(tenantId, id -> new TokenBucket(requestsPerMinute));
        return bucket.tryConsume();
    }

    private static class TokenBucket {
        private final long capacity;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(long capacity) {
            this.capacity = capacity;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume() {
            refill();

            if (tokens >= 1.0) {
                tokens -= 1.0;
                long remaining = (long) Math.floor(tokens);
                long resetSeconds = Math.max(1, (long) Math.ceil((capacity - tokens) / (capacity / 60.0)));
                return new RateLimitResult(true, capacity, remaining, resetSeconds);
            } else {
                long resetSeconds = Math.max(1, (long) Math.ceil((1.0 - tokens) / (capacity / 60.0)));
                return new RateLimitResult(false, capacity, 0, resetSeconds);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double secondsElapsed = (now - lastRefillTimestamp) / 1000.0;
            if (secondsElapsed > 0) {
                double tokensToAdd = secondsElapsed * (capacity / 60.0);
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
