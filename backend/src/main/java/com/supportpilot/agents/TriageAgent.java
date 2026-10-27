package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.llm.LlmClient;
import com.supportpilot.llm.LlmRequest;
import com.supportpilot.llm.LlmResponse;
import com.supportpilot.ticket.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * AI Agent responsible for classifying incoming support tickets into structured category, priority, and sentiment.
 */
@Component
public class TriageAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TriageAgent.class);
    private static final int MAX_PARSE_RETRIES = 2;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public TriageAgent(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/triage_v1.txt");
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load prompts/triage_v1.txt from classpath, using fallback prompt", e);
            return "Classify customer ticket:\nSubject: {subject}\nBody: {body}\nReturn JSON with category, priority, sentiment, summary, routeToResolutionAgent, routeToActionAgent.";
        }
    }

    @Override
    public AgentName name() {
        return AgentName.TRIAGE;
    }

    @Override
    public AgentResult run(AgentContext context) {
        Ticket ticket = context.ticket();
        long startTime = System.currentTimeMillis();

        String systemPrompt = "You are a professional support ticket triage classifier. Output strictly valid JSON matching the schema.";
        String userPrompt = promptTemplate
                .replace("{subject}", ticket.getSubject())
                .replace("{body}", ticket.getBody())
                .replace("{customerName}", ticket.getCustomerName())
                .replace("{customerEmail}", ticket.getCustomerEmail());

        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        LlmResponse lastResponse = null;
        TriageResult triageResult = null;

        for (int attempt = 0; attempt <= MAX_PARSE_RETRIES; attempt++) {
            try {
                lastResponse = llmClient.chat(new LlmRequest(systemPrompt, userPrompt));
                totalPromptTokens += lastResponse.promptTokens();
                totalCompletionTokens += lastResponse.completionTokens();

                String cleanJson = cleanJsonOutput(lastResponse.content());
                triageResult = objectMapper.readValue(cleanJson, TriageResult.class);

                // Validation passed
                break;
            } catch (Exception ex) {
                log.warn("Triage JSON parse attempt {} failed for ticket {}: {}", attempt + 1, ticket.getId(), ex.getMessage());
                if (attempt == MAX_PARSE_RETRIES) {
                    // Safe fallback default
                    triageResult = new TriageResult(
                            "GENERAL",
                            "MEDIUM",
                            "NEUTRAL",
                            "Ticket triage fallback",
                            true,
                            false
                    );
                } else {
                    userPrompt += "\nError: Invalid JSON output. Please respond with strict JSON only.";
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        String model = lastResponse != null ? lastResponse.model() : "mock-gemini-flash";
        var cost = lastResponse != null ? lastResponse.estimatedCostUsd() : java.math.BigDecimal.ZERO;

        return new AgentResult(
                name(),
                true,
                model,
                totalPromptTokens,
                totalCompletionTokens,
                cost,
                durationMs,
                triageResult,
                null,
                Map.of("category", triageResult.category(), "priority", triageResult.priority())
        );
    }

    private String cleanJsonOutput(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
