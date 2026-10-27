package com.supportpilot.agents;

/**
 * Structured output model for TriageAgent classification.
 */
public record TriageResult(
    String category,
    String priority,
    String sentiment,
    String summary,
    boolean routeToResolutionAgent,
    boolean routeToActionAgent
) {}
