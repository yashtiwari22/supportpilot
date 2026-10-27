package com.supportpilot.llm;

import java.math.BigDecimal;

/**
 * Normalized response from an LLM invocation.
 */
public record LlmResponse(
    String content,
    String model,
    int promptTokens,
    int completionTokens,
    BigDecimal estimatedCostUsd,
    long durationMs
) {}
