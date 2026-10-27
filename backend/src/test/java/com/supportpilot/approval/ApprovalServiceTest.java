package com.supportpilot.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.sse.SseHub;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.ticket.TicketRepository;
import com.supportpilot.workflow.TicketEventRepository;
import com.supportpilot.workflow.WorkflowState;
import com.supportpilot.workflow.WorkflowStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApprovalServiceTest {

    private ApprovalRepository approvalRepository;
    private TicketRepository ticketRepository;
    private TicketEventRepository ticketEventRepository;
    private WorkflowStateMachine stateMachine;
    private SseHub sseHub;
    private ObjectMapper objectMapper;
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalRepository = Mockito.mock(ApprovalRepository.class);
        ticketRepository = Mockito.mock(TicketRepository.class);
        ticketEventRepository = Mockito.mock(TicketEventRepository.class);
        stateMachine = new WorkflowStateMachine();
        sseHub = Mockito.mock(SseHub.class);
        objectMapper = new ObjectMapper();

        approvalService = new ApprovalService(
                approvalRepository,
                ticketRepository,
                ticketEventRepository,
                stateMachine,
                sseHub,
                objectMapper
        );
    }

    @Test
    @DisplayName("approve transitions approval to APPROVED and ticket to SENT")
    void approveTransitionsTicketToSent() {
        UUID approvalId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Approval approval = new Approval(ticketId, tenantId, "Draft reply", "{}", "Looks good");
        Ticket ticket = new Ticket(tenantId, "user@test.com", "User", "Help", "Body", "id-app-1");
        ticket.setId(ticketId);
        ticket.setStatus(WorkflowState.AWAITING_APPROVAL);

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        approvalService.approve(approvalId, reviewerId, "Sign off approved");

        assertEquals(Approval.Status.APPROVED, approval.getStatus());
        assertEquals(WorkflowState.SENT, ticket.getStatus());
        verify(ticketRepository, atLeastOnce()).save(ticket);
        verify(ticketEventRepository, atLeast(2)).save(any());
    }

    @Test
    @DisplayName("reject transitions approval to REJECTED and ticket to REJECTED")
    void rejectTransitionsTicketToRejected() {
        UUID approvalId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        Approval approval = new Approval(ticketId, tenantId, "Draft reply", "{}", "Check");
        Ticket ticket = new Ticket(tenantId, "user@test.com", "User", "Help", "Body", "id-app-2");
        ticket.setId(ticketId);
        ticket.setStatus(WorkflowState.AWAITING_APPROVAL);

        when(approvalRepository.findById(approvalId)).thenReturn(Optional.of(approval));
        when(ticketRepository.findById(ticketId)).thenReturn(Optional.of(ticket));

        approvalService.reject(approvalId, reviewerId, "Customer request violated policy");

        assertEquals(Approval.Status.REJECTED, approval.getStatus());
        assertEquals(WorkflowState.REJECTED, ticket.getStatus());
    }
}
