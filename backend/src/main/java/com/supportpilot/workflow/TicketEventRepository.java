package com.supportpilot.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for TicketEvent audit records.
 */
@Repository
public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID> {
    List<TicketEvent> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
