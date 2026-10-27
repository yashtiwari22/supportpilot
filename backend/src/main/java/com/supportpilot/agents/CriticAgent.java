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
 * AI Agent performing reflection, hallucination checks, policy compliance, and risk grading on draft responses.
 */
@Component
public class CriticAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(CriticAgent.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final String promptTemplate;

    public record CriticResult(
            String verdict, // APPROVED, NEEDS_REVISION, AWAITING_APPROVAL
            double confidence,
            boolean hallucinationDetected,
            boolean policyViolationDetected,
            String critiqueNotes,
            String revisionFeedback,
            boolean involvesFinancialAction
    ) {}

    public CriticAgent(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/critic_v1.txt");
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load prompts/critic_v1.txt, using fallback template", e);
            return "Review customer support draft:\nDraft: {draftReply}\nActions: {proposedAction}";
        }
    }

    @Override
    public AgentName name() {
        return AgentName.CRITIC;
    }

    @Override
    public AgentResult run(AgentContext context) {
        Ticket ticket = context.ticket();
        long startTime = System.currentTimeMillis();

        String draftReply = context.state().getOrDefault("draftReply", "").toString();
        String proposedActionJson = context.state().getOrDefault("proposedAction", "{}").toString();
        String kbSources = context.state().getOrDefault("knowledgeBaseSources", "[]").toString();
        boolean hasFinancialAction = proposedActionJson.contains("\"actionType\":\"REFUND\"")
                || proposedActionJson.contains("\"actionType\": \"REFUND\"");

        String userPrompt = promptTemplate
                .replace("{customerName}", ticket.getCustomerName())
                .replace("{customerEmail}", ticket.getCustomerEmail())
                .replace("{subject}", ticket.getSubject())
                .replace("{body}", ticket.getBody())
                .replace("{draftReply}", draftReply)
                .replace("{proposedAction}", proposedActionJson)
                .replace("{knowledgeBaseSources}", kbSources);

        String systemPrompt = "You are CriticAgent. Strictly audit response groundedness and policy safety. Output JSON.";
        LlmResponse llmResponse = llmClient.chat(new LlmRequest(systemPrompt, userPrompt));

        CriticResult result = parseCriticResult(llmResponse.content(), hasFinancialAction);
        long durationMs = System.currentTimeMillis() - startTime;

        return new AgentResult(
                name(),
                true,
                llmResponse.model(),
                llmResponse.promptTokens(),
                llmResponse.completionTokens(),
                llmResponse.estimatedCostUsd(),
                durationMs,
                result,
                null,
                Map.of(
                        "verdict", result.verdict(),
                        "confidence", result.confidence(),
                        "financialAction", result.involvesFinancialAction()
                )
        );
    }

    private CriticResult parseCriticResult(String rawJson, boolean hasFinancialAction) {
        try {
            String clean = rawJson.trim();
            if (clean.startsWith("```json")) clean = clean.substring(7);
            else if (clean.startsWith("```")) clean = clean.substring(3);
            if (clean.endsWith("```")) clean = clean.substring(0, clean.length() - 3);

            var tree = objectMapper.readTree(clean.trim());
            String verdict = tree.has("verdict") ? tree.get("verdict").asText() : "APPROVED";
            double conf = tree.has("confidence") ? tree.get("confidence").asDouble() : 0.95;
            boolean hallucination = tree.has("hallucinationDetected") && tree.get("hallucinationDetected").asBoolean();
            boolean policy = tree.has("policyViolationDetected") && tree.get("policyViolationDetected").asBoolean();
            String notes = tree.has("critiqueNotes") ? tree.get("critiqueNotes").asText() : "Verified grounded.";
            String feedback = tree.has("revisionFeedback") ? tree.get("revisionFeedback").asText() : "";

            // Enforce financial action safety: anything involving refunds requires human approval
            if (hasFinancialAction) {
                verdict = "AWAITING_APPROVAL";
                notes = "Financial refund action requires staff sign-off. " + notes;
            } else if (conf < 0.85) {
                verdict = "AWAITING_APPROVAL";
                notes = "Low critic confidence score. Escalated to human review. " + notes;
            }

            return new CriticResult(verdict, conf, hallucination, policy, notes, feedback, hasFinancialAction);
        } catch (Exception e) {
            log.warn("Failed to parse critic JSON, defaulting safely to AWAITING_APPROVAL: {}", e.getMessage());
            return new CriticResult(
                    "AWAITING_APPROVAL",
                    0.80,
                    false,
                    false,
                    "Critic evaluation fallback. Routed to staff approval.",
                    "",
                    hasFinancialAction
            );
        }
    }
}
