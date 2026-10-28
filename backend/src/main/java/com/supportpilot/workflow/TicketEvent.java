package com.supportpilot.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit log recording every state transition and lifecycle event for a ticket.
 */
@Entity
@Table(name = "ticket_events")
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 30)
    private WorkflowState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", length = 30)
    private WorkflowState toState;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TicketEvent() {}

    public TicketEvent(UUID ticketId, UUID tenantId, String eventType, WorkflowState fromState, WorkflowState toState, String payload) {
        this.ticketId = ticketId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.fromState = fromState;
        this.toState = toState;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTicketId() {
        return ticketId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEventType() {
        return eventType;
    }

    public WorkflowState getFromState() {
        return fromState;
    }

    public WorkflowState getToState() {
        return toState;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
