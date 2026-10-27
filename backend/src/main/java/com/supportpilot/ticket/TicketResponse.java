package com.supportpilot.ticket;

import com.supportpilot.workflow.WorkflowState;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard DTO representation of a Ticket.
 */
public record TicketResponse(
    UUID id,
    UUID tenantId,
    String customerEmail,
    String customerName,
    String subject,
    String body,
    WorkflowState status,
    String category,
    String priority,
    String sentiment,
    String idempotencyKey,
    Long version,
    Instant createdAt,
    Instant updatedAt
) {
    public static TicketResponse fromEntity(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTenantId(),
                ticket.getCustomerEmail(),
                ticket.getCustomerName(),
                ticket.getSubject(),
                ticket.getBody(),
                ticket.getStatus(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getSentiment(),
                ticket.getIdempotencyKey(),
                ticket.getVersion(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
