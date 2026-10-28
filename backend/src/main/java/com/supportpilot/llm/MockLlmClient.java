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
            boolean suspicious = userPrompt.contains("override") 
                    || userPrompt.contains("ignore all") 
                    || userPrompt.contains("unacceptable delay")
                    || userPrompt.contains("third time")
                    || userPrompt.contains("attacker")
                    || userPrompt.contains("store voucher");
            
            if (suspicious) {
                jsonContent = """
                {
                  "verdict": "AWAITING_APPROVAL",
                  "confidence": 0.70,
                  "hallucinationDetected": false,
                  "policyViolationDetected": true,
                  "critiqueNotes": "Ticket exhibits prompt injection or heightened escalation requiring human supervisory review.",
                  "revisionFeedback": "Routing to human agent approval queue.",
                  "involvesFinancialAction": false
                }
                """;
            } else {
                jsonContent = """
                {
                  "verdict": "APPROVED",
                  "confidence": 0.95,
                  "hallucinationDetected": false,
                  "policyViolationDetected": false,
                  "critiqueNotes": "Draft accurately addresses customer query with verified policy citations.",
                  "revisionFeedback": "",
                  "involvesFinancialAction": false
                }
                """;
            }
        } else {
            String citations = "[]";
            String reply;
            if (userPrompt.contains("warranty")) {
                reply = "According to our Warranty Policy, our standard 1-year manufacturer warranty covers defects in materials and workmanship, but does not cover accidental liquid damage.";
                citations = "[\"Standard Return and Refund Policy\"]";
            } else if (userPrompt.contains("return") || userPrompt.contains("refund")) {
                reply = "Under our Standard Return and Refund Policy, customers enjoy a 30-day return window from delivery date.";
                citations = "[\"Standard Return and Refund Policy\"]";
            } else {
                reply = "Thank you for contacting SupportPilot. We have received your request and our team is assisting you.";
            }

            jsonContent = String.format("""
            {
              "reply": "%s",
              "citations": %s,
              "confidence": 0.94
            }
            """, reply.replace("\"", "\\\""), citations);
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

        if (content.contains("ord-") || content.contains("tracking") || content.contains("shipped") || content.contains("package")) {
            category = "ORDER_INQUIRY";
            needsAction = true;
        } else if (content.contains("invoice") || content.contains("vat") || content.contains("charged") || content.contains("billing")) {
            category = "BILLING";
            needsAction = false;
        } else if (content.contains("warranty") || content.contains("broken") || content.contains("error") || content.contains("bluetooth") || content.contains("pair") || content.contains("fail")) {
            category = "TECHNICAL";
            needsAction = false;
        } else {
            category = "GENERAL";
            needsAction = false;
        }

        if (content.contains("angry") || content.contains("terrible") || content.contains("unacceptable") || content.contains("scam") || content.contains("immediately") || content.contains("urgent")) {
            priority = "URGENT";
            sentiment = "ANGRY";
        } else if (content.contains("override") || content.contains("ignore") || content.contains("attacker") || content.contains("damaged") || content.contains("not delivered") || content.contains("delay") || content.contains("waiting")) {
            priority = "HIGH";
            sentiment = content.contains("damaged") ? "NEGATIVE" : "NEUTRAL";
        } else if (content.contains("35 days") || content.contains("warranty") || content.contains("thanks") || content.contains("great") || content.contains("love")) {
            priority = "LOW";
            sentiment = "NEUTRAL";
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
