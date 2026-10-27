package com.supportpilot.approval;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a human-in-the-loop review item requiring staff sign-off.
 */
@Entity
@Table(name = "approvals")
public class Approval {

    public enum Status {
        PENDING,
        APPROVED,
        EDITED_APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "proposed_reply", nullable = false, columnDefinition = "TEXT")
    private String proposedReply;

    @Column(name = "proposed_action", columnDefinition = "jsonb")
    private String proposedAction;

    @Column(name = "critic_notes", columnDefinition = "TEXT")
    private String criticNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Approval() {}

    public Approval(UUID ticketId, UUID tenantId, String proposedReply, String proposedAction, String criticNotes) {
        this.ticketId = ticketId;
        this.tenantId = tenantId;
        this.proposedReply = proposedReply;
        this.proposedAction = proposedAction;
        this.criticNotes = criticNotes;
        this.status = Status.PENDING;
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

    public String getProposedReply() {
        return proposedReply;
    }

    public void setProposedReply(String proposedReply) {
        this.proposedReply = proposedReply;
    }

    public String getProposedAction() {
        return proposedAction;
    }

    public void setProposedAction(String proposedAction) {
        this.proposedAction = proposedAction;
    }

    public String getCriticNotes() {
        return criticNotes;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(UUID reviewerId) {
        this.reviewerId = reviewerId;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
