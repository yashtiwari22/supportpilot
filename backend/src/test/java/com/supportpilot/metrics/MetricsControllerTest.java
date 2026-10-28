package com.supportpilot.metrics;

import com.supportpilot.agents.AgentRunRepository;
import com.supportpilot.common.TenantContext;
import com.supportpilot.queue.QueueJobRepository;
import com.supportpilot.ticket.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class MetricsControllerTest {

    private TicketRepository ticketRepository;
    private AgentRunRepository agentRunRepository;
    private QueueJobRepository queueJobRepository;
    private MetricsController metricsController;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        ticketRepository = Mockito.mock(TicketRepository.class);
        agentRunRepository = Mockito.mock(AgentRunRepository.class);
        queueJobRepository = Mockito.mock(QueueJobRepository.class);
        metricsController = new MetricsController(ticketRepository, agentRunRepository, queueJobRepository);

        tenantId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getSummary_returnsAggregatedMetrics() {
        when(ticketRepository.countByTenantId(tenantId)).thenReturn(42L);
        when(ticketRepository.countByStatusForTenant(tenantId)).thenReturn(List.of());
        when(agentRunRepository.getAgentPerformanceSummary(tenantId)).thenReturn(List.of());

        ResponseEntity<MetricsSummaryResponse> response = metricsController.getSummary();
        assertNotNull(response.getBody());
        assertEquals(42L, response.getBody().totalTickets());
        assertNotNull(response.getBody().ticketsByState());
    }
}
