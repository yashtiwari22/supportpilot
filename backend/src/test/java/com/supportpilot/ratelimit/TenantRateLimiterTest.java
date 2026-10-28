package com.supportpilot.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantRateLimiterTest {

    @Test
    @DisplayName("Allows requests within capacity and tracks remaining tokens")
    void allowsRequestsWithinCapacity() {
        TenantRateLimiter limiter = new TenantRateLimiter(5, true);
        UUID tenantId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            TenantRateLimiter.RateLimitResult res = limiter.tryAcquire(tenantId);
            assertTrue(res.allowed(), "Request " + i + " should be allowed");
            assertEquals(5, res.limit());
            assertEquals(4 - i, res.remaining());
        }

        // 6th request should be rejected
        TenantRateLimiter.RateLimitResult rejected = limiter.tryAcquire(tenantId);
        assertFalse(rejected.allowed(), "Request exceeding quota should be rejected");
        assertEquals(0, rejected.remaining());
        assertTrue(rejected.resetSeconds() > 0);
    }

    @Test
    @DisplayName("Tenants have completely isolated rate limit buckets")
    void tenantsHaveIsolatedBuckets() {
        TenantRateLimiter limiter = new TenantRateLimiter(2, true);
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        // Exhaust Tenant A
        assertTrue(limiter.tryAcquire(tenantA).allowed());
        assertTrue(limiter.tryAcquire(tenantA).allowed());
        assertFalse(limiter.tryAcquire(tenantA).allowed());

        // Tenant B is unaffected
        assertTrue(limiter.tryAcquire(tenantB).allowed());
        assertTrue(limiter.tryAcquire(tenantB).allowed());
    }

    @Test
    @DisplayName("Bypasses rate limiting when disabled")
    void bypassesWhenDisabled() {
        TenantRateLimiter limiter = new TenantRateLimiter(1, false);
        UUID tenantId = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(tenantId).allowed());
        assertTrue(limiter.tryAcquire(tenantId).allowed());
        assertTrue(limiter.tryAcquire(tenantId).allowed());
    }
}
