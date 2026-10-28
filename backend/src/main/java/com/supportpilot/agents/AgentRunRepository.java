package com.supportpilot.agents;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for AgentRun records.
 */
@Repository
public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {
    List<AgentRun> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT r.agentName, COUNT(r), AVG(r.durationMs), SUM(r.promptTokens + r.completionTokens), SUM(r.estimatedCostUsd)
        FROM AgentRun r
        WHERE r.tenantId = :tenantId
        GROUP BY r.agentName
    """)
    List<Object[]> getAgentPerformanceSummary(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId);
}
