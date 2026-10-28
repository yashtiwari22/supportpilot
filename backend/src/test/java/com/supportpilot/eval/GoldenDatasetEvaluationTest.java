package com.supportpilot.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.agents.*;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.tools.SupportTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import com.supportpilot.agents.TriageResult;
import static org.mockito.Mockito.when;

class GoldenDatasetEvaluationTest {

    private ObjectMapper objectMapper;
    private TriageAgent triageAgent;
    private ActionAgent actionAgent;
    private CriticAgent criticAgent;
    private SupportTools supportTools;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        // Use mock LLM client for deterministic $0 CI evaluation
        var mockLlm = new com.supportpilot.llm.MockLlmClient(0, 0, 0.0);
        triageAgent = new TriageAgent(mockLlm, objectMapper);

        supportTools = Mockito.mock(SupportTools.class);
        actionAgent = new ActionAgent(supportTools, objectMapper);
        criticAgent = new CriticAgent(mockLlm, objectMapper);

        // Seed deterministic tool responses
        when(supportTools.lookupOrder(any(), eq("ORD-1042"), any()))
                .thenReturn(Map.of("found", true, "orderNumber", "ORD-1042", "status", "DELIVERED"));
        when(supportTools.checkRefundEligibility(any(), eq("ORD-1042")))
                .thenReturn(Map.of("eligible", true, "refundableAmount", new BigDecimal("149.99")));
        when(supportTools.proposeRefund(any(), eq("ORD-1042"), any(), any()))
                .thenReturn(Map.of("actionType", "REFUND", "orderNumber", "ORD-1042", "amount", new BigDecimal("149.99")));

        when(supportTools.lookupOrder(any(), eq("ORD-9999"), any()))
                .thenReturn(Map.of("found", false, "orderNumber", "ORD-9999"));
    }

    @Test
    @DisplayName("Golden Dataset: Evaluates triage classification accuracy across labeled test cases")
    void evaluateTriageAccuracyOnGoldenDataset() throws Exception {
        File datasetFile = new File("../eval/dataset.json");
        if (!datasetFile.exists()) {
            datasetFile = new File("eval/dataset.json");
        }

        assertTrue(datasetFile.exists(), "dataset.json should be accessible");

        JsonNode dataset = objectMapper.readTree(datasetFile);
        int total = dataset.size();
        int categoryMatches = 0;
        int actionMatches = 0;
        int criticGateMatches = 0;

        UUID tenantId = UUID.randomUUID();

        for (JsonNode tc : dataset) {
            String subject = tc.get("subject").asText();
            String body = tc.get("body").asText();
            String expectedCategory = tc.get("expectedCategory").asText();
            String expectedAction = tc.hasNonNull("expectedActionType") ? tc.get("expectedActionType").asText() : null;
            boolean expectedRequiresApproval = tc.get("expectedRequiresApproval").asBoolean();

            Ticket ticket = new Ticket(
                    tenantId,
                    tc.get("customerEmail").asText(),
                    tc.get("customerName").asText(),
                    subject,
                    body,
                    "eval-" + tc.get("id").asText()
            );

            // 1. Triage Agent
            AgentResult triageResult = triageAgent.run(new AgentContext(ticket));
            if (triageResult.payload() instanceof TriageResult tr) {
                if (tr.category().equalsIgnoreCase(expectedCategory)) {
                    categoryMatches++;
                }
                ticket.setCategory(tr.category());
                ticket.setPriority(tr.priority());
                ticket.setSentiment(tr.sentiment());
            }

            // 2. Action Agent
            AgentResult actionResult = actionAgent.run(new AgentContext(ticket));
            String actualAction = null;
            if (actionResult.payload() instanceof ActionAgent.ActionResult ar) {
                if (ar.proposedAction() != null) {
                    actualAction = ar.proposedAction().get("actionType").toString();
                } else if (ar.actionExecuted()) {
                    actualAction = "LOOKUP";
                }
            }
            if ((expectedAction == null && actualAction == null) ||
                (expectedAction != null && expectedAction.equals(actualAction))) {
                actionMatches++;
            }

            // 3. Critic Agent Gate
            Map<String, Object> criticState = new java.util.HashMap<>();
            if (actionResult.payload() instanceof ActionAgent.ActionResult ar && ar.proposedAction() != null) {
                criticState.put("proposedAction", objectMapper.writeValueAsString(ar.proposedAction()));
            }
            AgentResult criticResult = criticAgent.run(new AgentContext(ticket, criticState));
            if (criticResult.payload() instanceof CriticAgent.CriticResult cr) {
                // If refund proposed, awaiting approval verdict, or involves financial action -> requires human review
                boolean requiresReview = !"APPROVED".equalsIgnoreCase(cr.verdict()) || cr.involvesFinancialAction() || "REFUND".equals(actualAction);
                if (requiresReview == expectedRequiresApproval) {
                    criticGateMatches++;
                }
            }
        }

        double triageAccuracy = (double) categoryMatches / total;
        double actionAccuracy = (double) actionMatches / total;
        double criticAccuracy = (double) criticGateMatches / total;

        System.out.println("==================================================");
        System.out.println(" JUnit Golden Dataset Evaluation Results");
        System.out.printf(" Triage Intent Accuracy:  %.1f%%\n", triageAccuracy * 100);
        System.out.printf(" Tool Action Precision:   %.1f%%\n", actionAccuracy * 100);
        System.out.printf(" Critic Gate Accuracy:    %.1f%%\n", criticAccuracy * 100);
        System.out.println("==================================================");

        assertTrue(triageAccuracy >= 0.75, "Triage accuracy should be >= 75%");
        assertTrue(actionAccuracy >= 0.85, "Tool action precision should be >= 85%");
        assertTrue(criticAccuracy >= 0.85, "Critic gate accuracy should be >= 85%");
    }
}
