package com.supportpilot.metrics;

import com.supportpilot.queue.QueueJob;
import com.supportpilot.queue.QueueJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Centralized service publishing business and operational metrics to Micrometer / Prometheus.
 */
@Component
public class SupportPilotMetrics {

    private final MeterRegistry meterRegistry;
    private final QueueJobRepository queueJobRepository;

    public SupportPilotMetrics(MeterRegistry meterRegistry, QueueJobRepository queueJobRepository) {
        this.meterRegistry = meterRegistry;
        this.queueJobRepository = queueJobRepository;

        // Register DLQ depth gauge
        meterRegistry.gauge("supportpilot.queue.dlq.size", this, SupportPilotMetrics::getDlqSize);
    }

    private double getDlqSize() {
        try {
            return queueJobRepository.countByStatus(QueueJob.Status.DEAD_LETTER);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public void recordTicketIngested(UUID tenantId) {
        Counter.builder("supportpilot.tickets.ingested.total")
                .description("Total number of support tickets ingested")
                .tag("tenant", tenantId.toString())
                .register(meterRegistry)
                .increment();
    }

    public void recordTicketResolved(UUID tenantId, String resolutionStatus) {
        Counter.builder("supportpilot.tickets.resolved.total")
                .description("Total number of tickets reaching terminal resolution")
                .tag("tenant", tenantId.toString())
                .tag("status", resolutionStatus)
                .register(meterRegistry)
                .increment();
    }

    public void recordCriticInterception(UUID tenantId, String reason) {
        Counter.builder("supportpilot.critic.interceptions.total")
                .description("Total number of agent drafts intercepted by critic for human review")
                .tag("tenant", tenantId.toString())
                .tag("reason", reason != null ? reason : "low_confidence")
                .register(meterRegistry)
                .increment();
    }

    public void recordAgentDuration(String agentName, String status, long durationMs) {
        Timer.builder("supportpilot.agent.duration.seconds")
                .description("Execution latency of individual support agents")
                .tag("agent", agentName)
                .tag("status", status)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordRateLimitRejected(UUID tenantId) {
        Counter.builder("supportpilot.ratelimit.rejected.total")
                .description("Total number of ingestion requests rejected by multi-tenant rate limiter")
                .tag("tenant", tenantId != null ? tenantId.toString() : "unknown")
                .register(meterRegistry)
                .increment();
    }
}
