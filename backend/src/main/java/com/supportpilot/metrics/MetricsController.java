package com.supportpilot.metrics;

import com.supportpilot.agents.AgentRunRepository;
import com.supportpilot.common.TenantContext;
import com.supportpilot.queue.QueueJob;
import com.supportpilot.queue.QueueJobRepository;
import com.supportpilot.ticket.TicketRepository;
import com.supportpilot.workflow.WorkflowState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controller exposing aggregated operational, AI agent, and queue metrics for the console dashboard.
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final TicketRepository ticketRepository;
    private final AgentRunRepository agentRunRepository;
    private final QueueJobRepository queueJobRepository;

    public MetricsController(
            TicketRepository ticketRepository,
            AgentRunRepository agentRunRepository,
            QueueJobRepository queueJobRepository) {
        this.ticketRepository = ticketRepository;
        this.agentRunRepository = agentRunRepository;
        this.queueJobRepository = queueJobRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<MetricsSummaryResponse> getSummary() {
        UUID tenantId = TenantContext.requireTenantId();

        long totalTickets = ticketRepository.countByTenantId(tenantId);
        long queueDepth = queueJobRepository.countByStatus(QueueJob.Status.PENDING)
                + queueJobRepository.countByStatus(QueueJob.Status.PROCESSING);
        long deadLetterCount = queueJobRepository.countByStatus(QueueJob.Status.DEAD_LETTER);

        // Group tickets by state
        Map<String, Long> stateMap = new LinkedHashMap<>();
        for (WorkflowState ws : WorkflowState.values()) {
            stateMap.put(ws.name(), 0L);
        }
        for (Object[] row : ticketRepository.countByStatusForTenant(tenantId)) {
            WorkflowState state = (WorkflowState) row[0];
            Long count = (Long) row[1];
            stateMap.put(state.name(), count);
        }

        // Aggregate agent runs
        List<MetricsSummaryResponse.AgentMetricDto> agentMetrics = new ArrayList<>();
        for (Object[] row : agentRunRepository.getAgentPerformanceSummary(tenantId)) {
            String agentName = (String) row[0];
            Long count = (Long) row[1];
            Double avgDuration = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            Long tokens = row[3] != null ? ((Number) row[3]).longValue() : 0L;
            BigDecimal cost = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;

            agentMetrics.add(new MetricsSummaryResponse.AgentMetricDto(
                    agentName,
                    count,
                    Math.round(avgDuration * 10.0) / 10.0,
                    tokens,
                    cost
            ));
        }

        MetricsSummaryResponse response = new MetricsSummaryResponse(
                totalTickets,
                queueDepth,
                deadLetterCount,
                stateMap,
                agentMetrics,
                0L
        );

        return ResponseEntity.ok(response);
    }
}
