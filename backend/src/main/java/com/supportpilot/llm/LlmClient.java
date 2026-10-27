package com.supportpilot.llm;

/**
 * Port interface for LLM providers (Mock, Gemini, OpenAI).
 */
public interface LlmClient {

    /**
     * Executes a chat completion against the configured LLM provider.
     */
    LlmResponse chat(LlmRequest request);

    /**
     * Returns the provider identifier (e.g. "mock", "gemini").
     */
    String getProviderName();
}
