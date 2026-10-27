package com.supportpilot.ticket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.agents.AgentContext;
import com.supportpilot.agents.AgentResult;
import com.supportpilot.agents.AgentRun;
import com.supportpilot.agents.AgentRunRepository;
import com.supportpilot.agents.TriageAgent;
import com.supportpilot.agents.TriageResult;
import com.supportpilot.queue.TicketQueue;
import com.supportpilot.sse.SseHub;
import com.supportpilot.workflow.TicketEvent;
import com.supportpilot.workflow.TicketEventRepository;
import com.supportpilot.workflow.WorkflowOrchestrator;
import com.supportpilot.workflow.WorkflowState;
import com.supportpilot.workflow.WorkflowStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core application service managing ticket ingestion, queries, and workflow progression.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final TicketQueue ticketQueue;
    private final WorkflowStateMachine stateMachine;
    private final TriageAgent triageAgent;
    private final WorkflowOrchestrator workflowOrchestrator;
    private final SseHub sseHub;
    private final ObjectMapper objectMapper;

    public TicketService(
            TicketRepository ticketRepository,
            TicketEventRepository ticketEventRepository,
            AgentRunRepository agentRunRepository,
            TicketQueue ticketQueue,
            WorkflowStateMachine stateMachine,
            TriageAgent triageAgent,
            WorkflowOrchestrator workflowOrchestrator,
            SseHub sseHub,
            ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.ticketQueue = ticketQueue;
        this.stateMachine = stateMachine;
        this.triageAgent = triageAgent;
        this.workflowOrchestrator = workflowOrchestrator;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingests a new ticket with idempotency check, persists audit event, enqueues job, and emits SSE.
     */
    @Transactional
    public TicketResponse ingestTicket(UUID tenantId, CreateTicketRequest request, String idempotencyKey) {
        // 1. Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Ticket> existing = ticketRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent hit for ticket {} with key {}", existing.get().getId(), idempotencyKey);
                return TicketResponse.fromEntity(existing.get());
            }
        }

        // 2. Persist new ticket
        Ticket ticket = new Ticket(
                tenantId,
                request.customerEmail(),
                request.customerName(),
                request.subject(),
                request.body(),
                idempotencyKey
        );
        ticket = ticketRepository.save(ticket);

        // 3. Persist audit event
        recordEvent(ticket, "TICKET_RECEIVED", null, WorkflowState.RECEIVED, Map.of(
                "subject", ticket.getSubject(),
                "customerEmail", ticket.getCustomerEmail()
        ));

        // 4. Enqueue background job
        ticketQueue.enqueue(ticket.getId(), tenantId);

        // 5. Broadcast SSE event to connected console clients
        TicketResponse response = TicketResponse.fromEntity(ticket);
        sseHub.broadcast(tenantId, "ticket_created", response);

        log.info("Ingested and enqueued ticket {} for tenant {}", ticket.getId(), tenantId);
        return response;
    }

    /**
     * Processes triage step for a dequeued ticket.
     */
    @Transactional
    public void processTriageStep(UUID ticketId, UUID tenantId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        WorkflowState fromState = ticket.getStatus();
        WorkflowState toState = WorkflowState.TRIAGED;
        stateMachine.validateTransition(fromState, toState);

        // Execute TriageAgent
        AgentResult agentResult = triageAgent.run(new AgentContext(ticket));

        if (agentResult.payload() instanceof TriageResult triageResult) {
            ticket.setCategory(triageResult.category());
            ticket.setPriority(triageResult.priority());
            ticket.setSentiment(triageResult.sentiment());
        }

        ticket.setStatus(toState);
        ticketRepository.save(ticket);

        // Persist AgentRun metrics
        saveAgentRun(ticket, agentResult);

        // Persist state transition event
        recordEvent(ticket, "TICKET_TRIAGED", fromState, toState, Map.of(
                "category", ticket.getCategory() != null ? ticket.getCategory() : "GENERAL",
                "priority", ticket.getPriority() != null ? ticket.getPriority() : "MEDIUM",
                "sentiment", ticket.getSentiment() != null ? ticket.getSentiment() : "NEUTRAL"
        ));

        // Broadcast SSE updates
        sseHub.broadcast(tenantId, "ticket_state_changed", Map.of(
                "ticketId", ticket.getId().toString(),
                "status", ticket.getStatus().name(),
                "category", ticket.getCategory(),
                "priority", ticket.getPriority(),
                "sentiment", ticket.getSentiment(),
                "updatedAt", ticket.getUpdatedAt().toString()
        ));

        sseHub.broadcast(tenantId, "agent_step_completed", Map.of(
                "ticketId", ticket.getId().toString(),
                "agentName", agentResult.agentName().name(),
                "durationMs", agentResult.durationMs(),
                "promptTokens", agentResult.promptTokens(),
                "completionTokens", agentResult.completionTokens(),
                "costUsd", agentResult.estimatedCostUsd(),
                "status", agentResult.success() ? "SUCCESS" : "FAILED"
        ));

        log.info("Successfully triaged ticket {}: Category={}, Priority={}, Sentiment={}",
                ticket.getId(), ticket.getCategory(), ticket.getPriority(), ticket.getSentiment());

        // Launch full multi-agent pipeline (ResolutionAgent || ActionAgent -> CriticAgent -> Auto/Human Approval)
        if (agentResult.payload() instanceof TriageResult triageResult) {
            workflowOrchestrator.executeMultiAgentPipeline(ticket.getId(), triageResult);
        }
    }

    public List<TicketResponse> listTickets(UUID tenantId, WorkflowState status, String priority, String category, String search) {
        return ticketRepository.findFiltered(tenantId, status, priority, category, search)
                .stream()
                .map(TicketResponse::fromEntity)
                .toList();
    }

    public Optional<TicketResponse> getTicketById(UUID id, UUID tenantId) {
        return ticketRepository.findByIdAndTenantId(id, tenantId)
                .map(TicketResponse::fromEntity);
    }

    public List<TicketEvent> getTicketEvents(UUID ticketId) {
        return ticketEventRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public List<AgentRun> getTicketAgentRuns(UUID ticketId) {
        return agentRunRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    private void recordEvent(Ticket ticket, String eventType, WorkflowState fromState, WorkflowState toState, Map<String, Object> payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            TicketEvent event = new TicketEvent(ticket.getId(), ticket.getTenantId(), eventType, fromState, toState, payloadJson);
            ticketEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record ticket event {} for ticket {}", eventType, ticket.getId(), e);
        }
    }

    private void saveAgentRun(Ticket ticket, AgentResult result) {
        try {
            AgentRun run = new AgentRun();
            run.setTicketId(ticket.getId());
            run.setTenantId(ticket.getTenantId());
            run.setAgentName(result.agentName().name());
            run.setModel(result.model());
            run.setPromptTokens(result.promptTokens());
            run.setCompletionTokens(result.completionTokens());
            run.setEstimatedCostUsd(result.estimatedCostUsd());
            run.setDurationMs(result.durationMs());
            run.setStatus(result.success() ? "SUCCESS" : "FAILED");
            run.setOutputPayload(objectMapper.writeValueAsString(result.payload()));
            run.setErrorMessage(result.errorMessage());
            agentRunRepository.save(run);
        } catch (Exception e) {
            log.error("Failed to record agent run for ticket {}", ticket.getId(), e);
        }
    }
}
