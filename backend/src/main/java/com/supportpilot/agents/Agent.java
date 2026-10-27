package com.supportpilot.agents;

/**
 * Common interface for specialized AI agents.
 */
public interface Agent {

    /**
     * The unique role name of the agent.
     */
    AgentName name();

    /**
     * Executes the agent logic for the given ticket context.
     */
    AgentResult run(AgentContext context);
}
