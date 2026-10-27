package com.supportpilot.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

/**
 * Mock LLM client providing deterministic, cost-free responses with synthetic latency for testing and local dev.
 */
@Component("mockLlmClient")
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    private final int latencyMinMs;
    private final int latencyMaxMs;
    private final double failureRate;
    private final Random random = new Random();

    public MockLlmClient(
            @Value("${supportpilot.llm.mock.latency-min-ms:50}") int latencyMinMs,
            @Value("${supportpilot.llm.mock.latency-max-ms:150}") int latencyMaxMs,
            @Value("${supportpilot.llm.mock.failure-rate:0.0}") double failureRate) {
        this.latencyMinMs = latencyMinMs;
        this.latencyMaxMs = latencyMaxMs;
        this.failureRate = failureRate;
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        long startTime = System.currentTimeMillis();

        // Synthetic latency simulation
        int sleepMs = latencyMinMs;
        if (latencyMaxMs > latencyMinMs) {
            sleepMs += random.nextInt(latencyMaxMs - latencyMinMs);
        }
        if (sleepMs > 0) {
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Failure simulation
        if (failureRate > 0 && random.nextDouble() < failureRate) {
            throw new RuntimeException("Simulated MockLlmClient transient upstream failure");
        }

        String userPrompt = request.userPrompt().toLowerCase();
        String jsonContent;

        if (userPrompt.contains("triage") || request.systemPrompt().contains("triage")) {
            jsonContent = generateDeterministicTriageResponse(userPrompt);
        } else if (userPrompt.contains("critic") || request.systemPrompt().contains("critic")) {
            jsonContent = """
            {
              "verdict": "APPROVED",
              "confidence": 0.95,
              "critique": "Draft accurately addresses customer query and policy without hallucinations.",
              "requiresRevision": false
            }
            """;
        } else {
            jsonContent = """
            {
              "reply": "Thank you for contacting SupportPilot. We have received your request and our team is assisting you.",
              "citations": [],
              "confidence": 0.92
            }
            """;
        }

        long duration = System.currentTimeMillis() - startTime;
        int promptTokens = (int) (request.userPrompt().split("\\s+").length * 1.3) + 120;
        int completionTokens = (int) (jsonContent.split("\\s+").length * 1.3) + 20;
        BigDecimal cost = BigDecimal.valueOf((promptTokens * 0.0000005) + (completionTokens * 0.0000015))
                .setScale(6, RoundingMode.HALF_UP);

        return new LlmResponse(
                jsonContent.trim(),
                "mock-gemini-flash",
                promptTokens,
                completionTokens,
                cost,
                duration
        );
    }

    private String generateDeterministicTriageResponse(String text) {
        String category;
        String priority;
        String sentiment;
        boolean needsAction;

        // Isolate customer input section if full prompt is passed
        String content = text;
        int subjectIdx = text.indexOf("subject:");
        int schemaIdx = text.indexOf("you must output");
        if (schemaIdx == -1) schemaIdx = text.indexOf("schema:");
        if (subjectIdx != -1) {
            content = (schemaIdx != -1 && schemaIdx > subjectIdx) 
                    ? text.substring(subjectIdx, schemaIdx) 
                    : text.substring(subjectIdx);
        }

        if (content.contains("refund") || content.contains("return") || content.contains("charged") || content.contains("invoice")) {
            category = "BILLING";
            needsAction = true;
        } else if (content.contains("order") || content.contains("tracking") || content.contains("deliver") || content.contains("package") || content.contains("shipped")) {
            category = "ORDER_INQUIRY";
            needsAction = true;
        } else if (content.contains("broken") || content.contains("error") || content.contains("bluetooth") || content.contains("pair") || content.contains("fail")) {
            category = "TECHNICAL";
            needsAction = false;
        } else {
            category = "GENERAL";
            needsAction = false;
        }

        if (content.contains("angry") || content.contains("terrible") || content.contains("ridiculous") || content.contains("scam") || content.contains("immediately") || content.contains("urgent")) {
            priority = "URGENT";
            sentiment = "ANGRY";
        } else if (content.contains("not delivered") || content.contains("delay") || content.contains("waiting") || content.contains("disappointed")) {
            priority = "HIGH";
            sentiment = "NEGATIVE";
        } else if (content.contains("thanks") || content.contains("great") || content.contains("love")) {
            priority = "LOW";
            sentiment = "POSITIVE";
        } else {
            priority = "MEDIUM";
            sentiment = "NEUTRAL";
        }

        return String.format("""
        {
          "category": "%s",
          "priority": "%s",
          "sentiment": "%s",
          "summary": "Customer inquiry regarding %s.",
          "routeToResolutionAgent": true,
          "routeToActionAgent": %b
        }
        """, category, priority, sentiment, category.toLowerCase().replace('_', ' '), needsAction);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }
}
