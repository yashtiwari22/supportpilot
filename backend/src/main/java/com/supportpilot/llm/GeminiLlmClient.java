package com.supportpilot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * Gemini LLM client invoking Google AI Studio's OpenAI-compatible API with graceful fallback.
 */
@Component("geminiLlmClient")
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);
    private static final String GEMINI_OPENAI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MockLlmClient fallbackClient;

    public GeminiLlmClient(
            @Value("${supportpilot.llm.gemini.api-key:}") String apiKey,
            @Value("${supportpilot.llm.gemini.model:gemini-1.5-flash}") String model,
            ObjectMapper objectMapper,
            MockLlmClient fallbackClient) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.fallbackClient = fallbackClient;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("No GEMINI_API_KEY configured; gracefully utilizing deterministic fallback");
            return fallbackClient.chat(request);
        }

        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", request.systemPrompt()),
                            Map.of("role", "user", "content", request.userPrompt())
                    ),
                    "temperature", request.temperature() != null ? request.temperature() : 0.2,
                    "max_tokens", request.maxTokens() != null ? request.maxTokens() : 1024
            );

            String responseString = restClient.post()
                    .uri(GEMINI_OPENAI_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseString);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            int promptTokens = root.path("usage").path("prompt_tokens").asInt(100);
            int completionTokens = root.path("usage").path("completion_tokens").asInt(50);

            long durationMs = System.currentTimeMillis() - startTime;
            // Gemini Flash pricing: ~$0.075 / 1M prompt, $0.30 / 1M completion
            BigDecimal cost = BigDecimal.valueOf((promptTokens * 0.000000075) + (completionTokens * 0.000000300))
                    .setScale(6, RoundingMode.HALF_UP);

            return new LlmResponse(content, model, promptTokens, completionTokens, cost, durationMs);
        } catch (Exception e) {
            log.warn("Gemini upstream call encountered an error: {}. Activating resilient fallback.", e.getMessage());
            return fallbackClient.chat(request);
        }
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }
}
