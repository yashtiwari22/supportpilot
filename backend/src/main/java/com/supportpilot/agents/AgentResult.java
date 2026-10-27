package com.supportpilot.agents;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Result output of an AI agent execution, including cost, token metrics, and output payload.
 */
public record AgentResult(
    AgentName agentName,
    boolean success,
    String model,
    int promptTokens,
    int completionTokens,
    BigDecimal estimatedCostUsd,
    long durationMs,
    Object payload,
    String errorMessage,
    Map<String, Object> metadata
) {
    public static AgentResult failure(AgentName agentName, String model, String errorMessage, long durationMs) {
        return new AgentResult(
                agentName,
                false,
                model,
                0,
                0,
                BigDecimal.ZERO,
                durationMs,
                null,
                errorMessage,
                Map.of()
        );
    }
}
