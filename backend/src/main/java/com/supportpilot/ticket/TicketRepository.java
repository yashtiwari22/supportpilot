package com.supportpilot.ticket;

import com.supportpilot.workflow.WorkflowState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for Ticket entities.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<Ticket> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.tenantId = :tenantId
          AND (:status IS NULL OR t.status = :status)
          AND (:priority IS NULL OR t.priority = :priority)
          AND (:category IS NULL OR t.category = :category)
          AND (:search IS NULL OR LOWER(t.subject) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(t.customerName) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(t.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY t.createdAt DESC
    """)
    List<Ticket> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("status") WorkflowState status,
            @Param("priority") String priority,
            @Param("category") String category,
            @Param("search") String search
    );

    List<Ticket> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    @Query("SELECT t.status, COUNT(t) FROM Ticket t WHERE t.tenantId = :tenantId GROUP BY t.status")
    List<Object[]> countByStatusForTenant(@Param("tenantId") UUID tenantId);

    long countByTenantId(UUID tenantId);
}
