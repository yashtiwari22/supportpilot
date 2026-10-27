package com.supportpilot.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.sse.SseHub;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.ticket.TicketRepository;
import com.supportpilot.workflow.TicketEvent;
import com.supportpilot.workflow.TicketEventRepository;
import com.supportpilot.workflow.WorkflowState;
import com.supportpilot.workflow.WorkflowStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service managing staff approvals, edits, and rejections of staged AI draft responses and actions.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;
    private final TicketRepository ticketRepository;
    private final TicketEventRepository ticketEventRepository;
    private final WorkflowStateMachine stateMachine;
    private final SseHub sseHub;
    private final ObjectMapper objectMapper;

    public record ApprovalDto(
            UUID id,
            UUID ticketId,
            String customerName,
            String customerEmail,
            String ticketSubject,
            String proposedReply,
            String proposedAction,
            String criticNotes,
            String status,
            Instant createdAt
    ) {}

    public ApprovalService(
            ApprovalRepository approvalRepository,
            TicketRepository ticketRepository,
            TicketEventRepository ticketEventRepository,
            WorkflowStateMachine stateMachine,
            SseHub sseHub,
            ObjectMapper objectMapper) {
        this.approvalRepository = approvalRepository;
        this.ticketRepository = ticketRepository;
        this.ticketEventRepository = ticketEventRepository;
        this.stateMachine = stateMachine;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
    }

    public List<ApprovalDto> listPendingApprovals(UUID tenantId) {
        return approvalRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, Approval.Status.PENDING)
                .stream()
                .map(a -> {
                    Ticket ticket = ticketRepository.findById(a.getTicketId()).orElse(null);
                    return new ApprovalDto(
                            a.getId(),
                            a.getTicketId(),
                            ticket != null ? ticket.getCustomerName() : "Unknown",
                            ticket != null ? ticket.getCustomerEmail() : "",
                            ticket != null ? ticket.getSubject() : "No Subject",
                            a.getProposedReply(),
                            a.getProposedAction(),
                            a.getCriticNotes(),
                            a.getStatus().name(),
                            a.getCreatedAt()
                    );
                })
                .toList();
    }

    @Transactional
    public void approve(UUID approvalId, UUID reviewerId, String reviewNotes) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        approval.setStatus(Approval.Status.APPROVED);
        approval.setReviewerId(reviewerId);
        approval.setReviewNotes(reviewNotes);
        approval.setReviewedAt(Instant.now());
        approvalRepository.save(approval);

        Ticket ticket = ticketRepository.findById(approval.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + approval.getTicketId()));

        transitionTicket(ticket, WorkflowState.APPROVED, "HUMAN_APPROVED", Map.of(
                "reviewerId", reviewerId != null ? reviewerId.toString() : "system",
                "notes", reviewNotes != null ? reviewNotes : ""
        ));

        // Dispatch reply -> transition to SENT
        transitionTicket(ticket, WorkflowState.SENT, "TICKET_SENT", Map.of(
                "channel", "EMAIL",
                "recipient", ticket.getCustomerEmail()
        ));

        log.info("Approval {} approved for ticket {}", approvalId, ticket.getId());
    }

    @Transactional
    public void editAndApprove(UUID approvalId, UUID reviewerId, String editedReply, String reviewNotes) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        approval.setStatus(Approval.Status.EDITED_APPROVED);
        approval.setProposedReply(editedReply);
        approval.setReviewerId(reviewerId);
        approval.setReviewNotes(reviewNotes);
        approval.setReviewedAt(Instant.now());
        approvalRepository.save(approval);

        Ticket ticket = ticketRepository.findById(approval.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + approval.getTicketId()));

        transitionTicket(ticket, WorkflowState.APPROVED, "HUMAN_EDITED_AND_APPROVED", Map.of(
                "reviewerId", reviewerId != null ? reviewerId.toString() : "system",
                "notes", reviewNotes != null ? reviewNotes : "",
                "editedReplyLength", editedReply.length()
        ));

        transitionTicket(ticket, WorkflowState.SENT, "TICKET_SENT", Map.of(
                "channel", "EMAIL",
                "recipient", ticket.getCustomerEmail()
        ));

        log.info("Approval {} edited and approved for ticket {}", approvalId, ticket.getId());
    }

    @Transactional
    public void reject(UUID approvalId, UUID reviewerId, String reason) {
        Approval approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        approval.setStatus(Approval.Status.REJECTED);
        approval.setReviewerId(reviewerId);
        approval.setReviewNotes(reason);
        approval.setReviewedAt(Instant.now());
        approvalRepository.save(approval);

        Ticket ticket = ticketRepository.findById(approval.getTicketId())
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + approval.getTicketId()));

        transitionTicket(ticket, WorkflowState.REJECTED, "HUMAN_REJECTED", Map.of(
                "reviewerId", reviewerId != null ? reviewerId.toString() : "system",
                "reason", reason != null ? reason : ""
        ));

        log.info("Approval {} rejected for ticket {}: {}", approvalId, ticket.getId(), reason);
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
}
