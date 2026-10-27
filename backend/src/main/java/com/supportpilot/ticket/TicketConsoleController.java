package com.supportpilot.ticket;

import com.supportpilot.agents.AgentRun;
import com.supportpilot.common.TenantContext;
import com.supportpilot.workflow.TicketEvent;
import com.supportpilot.workflow.WorkflowState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing ticket queries and audit timelines to the staff Next.js console.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketConsoleController {

    private final TicketService ticketService;

    public TicketConsoleController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    public ResponseEntity<List<TicketResponse>> listTickets(
            @RequestParam(required = false) WorkflowState status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {

        UUID tenantId = TenantContext.requireTenantId();
        List<TicketResponse> tickets = ticketService.listTickets(tenantId, status, priority, category, search);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
        UUID tenantId = TenantContext.requireTenantId();
        return ticketService.getTicketById(id, tenantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<List<TicketEvent>> getTicketEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketEvents(id));
    }

    @GetMapping("/{id}/agent-runs")
    public ResponseEntity<List<AgentRun>> getTicketAgentRuns(@PathVariable UUID id) {
        return ResponseEntity.ok(ticketService.getTicketAgentRuns(id));
    }
}
