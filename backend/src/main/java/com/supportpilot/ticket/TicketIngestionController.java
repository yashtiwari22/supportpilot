package com.supportpilot.ticket;

import com.supportpilot.common.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller handling external ticket ingestion with API key authentication and idempotency keys.
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketIngestionController {

    private final TicketService ticketService;

    public TicketIngestionController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTicketRequest request) {

        UUID tenantId = TenantContext.requireTenantId();
        TicketResponse response = ticketService.ingestTicket(tenantId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
