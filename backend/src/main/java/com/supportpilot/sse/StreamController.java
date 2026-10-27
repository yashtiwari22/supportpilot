package com.supportpilot.sse;

import com.supportpilot.common.TenantContext;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Controller exposing the Server-Sent Events stream for real-time console updates.
 */
@RestController
@RequestMapping("/api/v1")
public class StreamController {

    private final SseHub sseHub;

    public StreamController(SseHub sseHub) {
        this.sseHub = sseHub;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
            HttpServletResponse response) {

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // Default to demo tenant if accessed directly in dev
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        // Disable buffering across proxies (Nginx, Vercel, Cloudflare, Render)
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");

        return sseHub.register(tenantId, lastEventId);
    }
}
