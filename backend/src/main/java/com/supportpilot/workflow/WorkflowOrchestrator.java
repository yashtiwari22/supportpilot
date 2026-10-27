package com.supportpilot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.agents.*;
import com.supportpilot.approval.Approval;
import com.supportpilot.approval.ApprovalRepository;
import com.supportpilot.sse.SseHub;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.ticket.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Orchestrator coordinating parallel agent fan-out, Critic reflection loops, and approval routing.
 */
@Service
public class WorkflowOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WorkflowOrchestrator.class);
    private static final int MAX_REVISION_LOOPS = 2;
    private static final long AGENT_TIMEOUT_SECONDS = 5;

    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final AgentRunRepository agentRunRepository;
    private final ApprovalRepository approvalRepository;
    private final WorkflowStateMachine stateMachine;
    private final ResolutionAgent resolutionAgent;
    private final ActionAgent actionAgent;
    private final CriticAgent criticAgent;
    private final SseHub sseHub;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public WorkflowOrchestrator(
            TicketRepository ticketRepository,
            TicketEventRepository ticketEventRepository,
            AgentRunRepository agentRunRepository,
            ApprovalRepository approvalRepository,
            WorkflowStateMachine stateMachine,
            ResolutionAgent resolutionAgent,
            ActionAgent actionAgent,
            CriticAgent criticAgent,
            SseHub sseHub,
            ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.agentRunRepository = agentRunRepository;
        this.approvalRepository = approvalRepository;
        this.stateMachine = stateMachine;
        this.resolutionAgent = resolutionAgent;
        this.actionAgent = actionAgent;
        this.criticAgent = criticAgent;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the full multi-agent pipeline following triage classification.
     */
    @Transactional
    public void executeMultiAgentPipeline(UUID ticketId, TriageResult triageResult) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + ticketId));

        // 1. Move to PROCESSING
        transitionTicket(ticket, WorkflowState.PROCESSING, "PIPELINE_STARTED", Map.of(
                "routeToResolution", triageResult.routeToResolutionAgent(),
                "routeToAction", triageResult.routeToActionAgent()
        ));

        int revisionLoop = 0;
        String draftReply = "";
        Map<String, Object> proposedAction = null;
        CriticAgent.CriticResult criticResult = null;

        while (revisionLoop <= MAX_REVISION_LOOPS) {
            // 2. Parallel Fan-Out: ResolutionAgent || ActionAgent
            Map<String, Object> parallelResults = executeParallelFanOut(ticket, triageResult);
            draftReply = (String) parallelResults.getOrDefault("draftReply", "");
            @SuppressWarnings("unchecked")
            Map<String, Object> action = (Map<String, Object>) parallelResults.get("proposedAction");
            proposedAction = action;

            // Transition to DRAFTED
            if (ticket.getStatus() == WorkflowState.PROCESSING || ticket.getStatus() == WorkflowState.REVISING) {
                transitionTicket(ticket, WorkflowState.DRAFTED, "AGENTS_DRAFTED", Map.of(
                        "draftLength", draftReply.length(),
                        "hasProposedAction", proposedAction != null
                ));
            }

            // 3. CriticAgent Evaluation
            Map<String, Object> criticState = new HashMap<>();
            criticState.put("draftReply", draftReply);
            criticState.put("proposedAction", proposedAction != null ? toJson(proposedAction) : "{}");
            criticState.put("revisionLoop", revisionLoop);

            AgentResult criticRun = criticAgent.run(new AgentContext(ticket, criticState));
            saveAgentRun(ticket, criticRun);
            criticResult = (CriticAgent.CriticResult) criticRun.payload();

            transitionTicket(ticket, WorkflowState.CRITIQUED, "CRITIC_EVALUATED", Map.of(
                    "verdict", criticResult.verdict(),
                    "confidence", criticResult.confidence(),
                    "notes", criticResult.critiqueNotes()
            ));

            if ("NEEDS_REVISION".equals(criticResult.verdict()) && revisionLoop < MAX_REVISION_LOOPS) {
                log.info("Critic requested revision for ticket {}. Loop {}/{}", ticketId, revisionLoop + 1, MAX_REVISION_LOOPS);
                transitionTicket(ticket, WorkflowState.REVISING, "REVISION_REQUESTED", Map.of(
                        "feedback", criticResult.revisionFeedback(),
                        "loop", revisionLoop + 1
                ));
                revisionLoop++;
            } else {
                break;
            }
        }

        // 4. Decision: Auto-approval vs Approval Queue
        if (criticResult != null && "APPROVED".equals(criticResult.verdict()) && !criticResult.involvesFinancialAction()) {
            log.info("Ticket {} auto-approved by critic (confidence={})", ticketId, criticResult.confidence());
            transitionTicket(ticket, WorkflowState.APPROVED, "AUTO_APPROVED", Map.of(
                    "confidence", criticResult.confidence(),
                    "draftReply", draftReply
            ));
            transitionTicket(ticket, WorkflowState.SENT, "TICKET_SENT", Map.of(
                    "channel", "EMAIL",
                    "recipient", ticket.getCustomerEmail()
            ));
        } else {
            // High risk, financial action, or low confidence -> Route to human approval queue
            log.info("Ticket {} routed to human approval queue", ticketId);
            transitionTicket(ticket, WorkflowState.AWAITING_APPROVAL, "APPROVAL_REQUIRED", Map.of(
                    "critiqueNotes", criticResult != null ? criticResult.critiqueNotes() : "Requires human review",
                    "involvesFinancialAction", criticResult != null && criticResult.involvesFinancialAction()
            ));

            Approval approval = new Approval(
                    ticket.getId(),
                    ticket.getTenantId(),
                    draftReply,
                    proposedAction != null ? toJson(proposedAction) : "{}",
                    criticResult != null ? criticResult.critiqueNotes() : "Requires staff confirmation"
            );
            approvalRepository.save(approval);

            sseHub.broadcast(ticket.getTenantId(), "approval_requested", Map.of(
                    "approvalId", approval.getId().toString(),
                    "ticketId", ticket.getId().toString(),
                    "subject", ticket.getSubject(),
                    "customerName", ticket.getCustomerName()
            ));
        }
    }

    private Map<String, Object> executeParallelFanOut(Ticket ticket, TriageResult triageResult) {
        CompletableFuture<AgentResult> resolutionFuture = CompletableFuture.supplyAsync(() -> {
            if (triageResult.routeToResolutionAgent()) {
                AgentResult res = resolutionAgent.run(new AgentContext(ticket));
                saveAgentRun(ticket, res);
                return res;
            }
            return null;
        }, virtualThreadExecutor);

        CompletableFuture<AgentResult> actionFuture = CompletableFuture.supplyAsync(() -> {
            if (triageResult.routeToActionAgent()) {
                AgentResult res = actionAgent.run(new AgentContext(ticket));
                saveAgentRun(ticket, res);
                return res;
            }
            return null;
        }, virtualThreadExecutor);

        Map<String, Object> results = new HashMap<>();

        try {
            CompletableFuture.allOf(resolutionFuture, actionFuture)
                    .get(AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            AgentResult resRun = resolutionFuture.getNow(null);
            AgentResult actRun = actionFuture.getNow(null);

            if (resRun != null && resRun.payload() instanceof ResolutionAgent.ResolutionResult resResult) {
                results.put("draftReply", resResult.draftReply());
                results.put("citations", resResult.citations());
            }

            if (actRun != null && actRun.payload() instanceof ActionAgent.ActionResult actResult) {
                results.put("actionExecuted", actResult.actionExecuted());
                results.put("proposedAction", actResult.proposedAction());
            }
        } catch (Exception e) {
            log.warn("Parallel agent fan-out hit partial error or timeout for ticket {}: {}", ticket.getId(), e.getMessage());
            results.putIfAbsent("draftReply", "Thank you for contacting us. We have received your inquiry and our staff will respond shortly.");
        }

        return results;
    }

    private void transitionTicket(Ticket ticket, WorkflowState toState, String eventType, Map<String, Object> payload) {
        WorkflowState fromState = ticket.getStatus();
        stateMachine.validateTransition(fromState, toState);
        ticket.setStatus(toState);
        ticketRepository.save(ticket);

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            TicketEvent event = new TicketEvent(ticket.getId(), ticket.getTenantId(), eventType, fromState, toState, payloadJson);
            ticketEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to record event {} on ticket {}", eventType, ticket.getId(), e);
        }

        sseHub.broadcast(ticket.getTenantId(), "ticket_state_changed", Map.of(
                "ticketId", ticket.getId().toString(),
                "status", ticket.getStatus().name(),
                "updatedAt", ticket.getUpdatedAt().toString()
        ));
    }

    private void saveAgentRun(Ticket ticket, AgentResult result) {
        if (result == null) return;
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

            sseHub.broadcast(ticket.getTenantId(), "agent_step_completed", Map.of(
                    "ticketId", ticket.getId().toString(),
                    "agentName", result.agentName().name(),
                    "durationMs", result.durationMs(),
                    "tokens", result.promptTokens() + result.completionTokens(),
                    "costUsd", result.estimatedCostUsd(),
                    "status", run.getStatus()
            ));
        } catch (Exception e) {
            log.error("Failed to save agent run: {}", e.getMessage(), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
