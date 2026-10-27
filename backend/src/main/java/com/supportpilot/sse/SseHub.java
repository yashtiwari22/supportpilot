package com.supportpilot.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages Server-Sent Events (SSE) connections scoped by tenant.
 */
@Component
public class SseHub {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);
    private static final long EMITTER_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final Map<UUID, List<SseEmitter>> tenantEmitters = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong(0);

    /**
     * Registers a new SSE client connection for a specific tenant.
     */
    public SseEmitter register(UUID tenantId, String lastEventId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        tenantEmitters.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError(ex -> removeEmitter(tenantId, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .id(String.valueOf(eventSequence.incrementAndGet()))
                    .data(Map.of("message", "Connected to SupportPilot stream", "tenantId", tenantId.toString())));
        } catch (IOException e) {
            removeEmitter(tenantId, emitter);
        }

        return emitter;
    }

    /**
     * Broadcasts an event to all connected clients of a specific tenant.
     */
    public void broadcast(UUID tenantId, String eventName, Object payload) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        String eventId = String.valueOf(eventSequence.incrementAndGet());
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .id(eventId)
                        .data(payload));
            } catch (Exception ex) {
                deadEmitters.add(emitter);
            }
        }

        if (!deadEmitters.isEmpty()) {
            emitters.removeAll(deadEmitters);
        }
    }

    private void removeEmitter(UUID tenantId, SseEmitter emitter) {
        List<SseEmitter> emitters = tenantEmitters.get(tenantId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                tenantEmitters.remove(tenantId);
            }
        }
    }

    /**
     * Periodic heartbeat to prevent proxies and browsers from terminating idle SSE connections.
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeats() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : tenantEmitters.entrySet()) {
            List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (Exception ex) {
                    deadEmitters.add(emitter);
                }
            }
            if (!deadEmitters.isEmpty()) {
                entry.getValue().removeAll(deadEmitters);
            }
        }
    }
}
