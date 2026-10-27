package com.supportpilot.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.agents.AgentRunRepository;
import com.supportpilot.agents.TriageAgent;
import com.supportpilot.queue.TicketQueue;
import com.supportpilot.sse.SseHub;
import com.supportpilot.workflow.TicketEventRepository;
import com.supportpilot.workflow.WorkflowOrchestrator;
import com.supportpilot.workflow.WorkflowStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TicketIngestionIdempotencyTest {

    private TicketRepository ticketRepository;
    private TicketEventRepository ticketEventRepository;
    private AgentRunRepository agentRunRepository;
    private TicketQueue ticketQueue;
    private WorkflowStateMachine stateMachine;
    private TriageAgent triageAgent;
    private SseHub sseHub;
    private ObjectMapper objectMapper;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketRepository = Mockito.mock(TicketRepository.class);
        ticketEventRepository = Mockito.mock(TicketEventRepository.class);
        agentRunRepository = Mockito.mock(AgentRunRepository.class);
        ticketQueue = Mockito.mock(TicketQueue.class);
        stateMachine = new WorkflowStateMachine();
        triageAgent = Mockito.mock(TriageAgent.class);
        sseHub = Mockito.mock(SseHub.class);
        objectMapper = new ObjectMapper();
        WorkflowOrchestrator workflowOrchestrator = Mockito.mock(WorkflowOrchestrator.class);

        ticketService = new TicketService(
                ticketRepository,
                ticketEventRepository,
                agentRunRepository,
                ticketQueue,
                stateMachine,
                triageAgent,
                workflowOrchestrator,
                sseHub,
                objectMapper
        );
    }

    @Test
    @DisplayName("Idempotent ingestion returns existing ticket and skips re-enqueuing")
    void duplicateIdempotencyKeyReturnsExistingTicket() {
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = "idemp-unique-12345";
        CreateTicketRequest request = new CreateTicketRequest(
                "john@example.com", "John Doe", "Subject", "Body"
        );

        Ticket existingTicket = new Ticket(
                tenantId, request.customerEmail(), request.customerName(), request.subject(), request.body(), idempotencyKey
        );
        existingTicket.setId(UUID.randomUUID());

        when(ticketRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .thenReturn(Optional.of(existingTicket));

        TicketResponse response = ticketService.ingestTicket(tenantId, request, idempotencyKey);

        assertEquals(existingTicket.getId(), response.id());
        verify(ticketQueue, never()).enqueue(any(), any());
        verify(ticketRepository, never()).save(any());
        verify(sseHub, never()).broadcast(any(), eq("ticket_created"), any());
    }

    @Test
    @DisplayName("New ingestion saves ticket, audits event, and enqueues job")
    void newIngestionEnqueuesAndEmitsSse() {
        UUID tenantId = UUID.randomUUID();
        String idempotencyKey = "idemp-unique-99999";
        CreateTicketRequest request = new CreateTicketRequest(
                "jane@example.com", "Jane Doe", "Order inquiry", "Where is my item?"
        );

        when(ticketRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey))
                .thenReturn(Optional.empty());

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> {
            Ticket t = invocation.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });

        TicketResponse response = ticketService.ingestTicket(tenantId, request, idempotencyKey);

        assertNotNull(response.id());
        assertEquals("Where is my item?", response.body());
        verify(ticketRepository).save(any(Ticket.class));
        verify(ticketEventRepository).save(any());
        verify(ticketQueue).enqueue(eq(response.id()), eq(tenantId));
        verify(sseHub).broadcast(eq(tenantId), eq("ticket_created"), any());
    }
}
