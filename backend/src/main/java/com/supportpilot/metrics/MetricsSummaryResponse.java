package com.supportpilot.metrics;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Data transfer object aggregating live operational and AI performance metrics.
 */
public record MetricsSummaryResponse(
        long totalTickets,
        long queueDepth,
        long deadLetterCount,
        Map<String, Long> ticketsByState,
        List<AgentMetricDto> agentMetrics,
        long rateLimitRejections
) {
    public record AgentMetricDto(
            String agentName,
            long totalRuns,
            double averageDurationMs,
            long totalTokens,
            BigDecimal totalCostUsd
    ) {}
}
