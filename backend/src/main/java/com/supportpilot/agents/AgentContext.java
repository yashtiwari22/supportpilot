package com.supportpilot.agents;

import com.supportpilot.ticket.Ticket;

import java.util.Map;

/**
 * Execution context provided to an AI agent run.
 */
public record AgentContext(
    Ticket ticket,
    Map<String, Object> state
) {
    public AgentContext(Ticket ticket) {
        this(ticket, Map.of());
    }
}
