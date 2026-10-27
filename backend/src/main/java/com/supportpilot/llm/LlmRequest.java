package com.supportpilot.llm;

import java.util.List;
import java.util.Map;

/**
 * Request payload for LLM completions.
 */
public record LlmRequest(
    String systemPrompt,
    String userPrompt,
    Double temperature,
    Integer maxTokens,
    Map<String, Object> metadata
) {
    public LlmRequest(String systemPrompt, String userPrompt) {
        this(systemPrompt, userPrompt, 0.2, 1024, Map.of());
    }
}
