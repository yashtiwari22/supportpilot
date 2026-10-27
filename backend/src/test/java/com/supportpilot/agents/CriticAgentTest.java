package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.llm.LlmClient;
import com.supportpilot.llm.LlmRequest;
import com.supportpilot.llm.LlmResponse;
import com.supportpilot.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CriticAgentTest {

    private LlmClient mockLlm;
    private ObjectMapper objectMapper;
    private CriticAgent criticAgent;

    @BeforeEach
    void setUp() {
        mockLlm = Mockito.mock(LlmClient.class);
        objectMapper = new ObjectMapper();
        criticAgent = new CriticAgent(mockLlm, objectMapper);
    }

    @Test
    @DisplayName("Critic auto-approves safe high-confidence response without money")
    void criticAutoApprovesSafeResponse() {
        String json = """
        {
          "verdict": "APPROVED",
          "confidence": 0.95,
          "hallucinationDetected": false,
          "policyViolationDetected": false,
          "critiqueNotes": "All claims are grounded in return policy.",
          "revisionFeedback": "",
          "involvesFinancialAction": false
        }
        """;
        when(mockLlm.chat(any(LlmRequest.class))).thenReturn(new LlmResponse(
                json, "mock-gemini", 100, 30, BigDecimal.ZERO, 50
        ));

        Ticket ticket = new Ticket(UUID.randomUUID(), "a@b.com", "A", "Sub", "Body", "id-1");
        AgentResult result = criticAgent.run(new AgentContext(ticket, Map.of("draftReply", "Safe draft")));

        CriticAgent.CriticResult critic = (CriticAgent.CriticResult) result.payload();
        assertEquals("APPROVED", critic.verdict());
        assertFalse(critic.involvesFinancialAction());
    }

    @Test
    @DisplayName("Critic escalates to AWAITING_APPROVAL whenever a financial refund is involved")
    void criticForcesApprovalForRefundAction() {
        String json = """
        {
          "verdict": "APPROVED",
          "confidence": 0.95,
          "hallucinationDetected": false,
          "policyViolationDetected": false,
          "critiqueNotes": "Grounded draft.",
          "revisionFeedback": "",
          "involvesFinancialAction": true
        }
        """;
        when(mockLlm.chat(any(LlmRequest.class))).thenReturn(new LlmResponse(
                json, "mock-gemini", 100, 30, BigDecimal.ZERO, 50
        ));

        Ticket ticket = new Ticket(UUID.randomUUID(), "a@b.com", "A", "Sub", "Body", "id-2");
        Map<String, Object> state = Map.of(
                "draftReply", "We are issuing a refund of $149.99.",
                "proposedAction", "{\"actionType\":\"REFUND\",\"amount\":149.99}"
        );

        AgentResult result = criticAgent.run(new AgentContext(ticket, state));
        CriticAgent.CriticResult critic = (CriticAgent.CriticResult) result.payload();

        // Safety guarantee: must be AWAITING_APPROVAL
        assertEquals("AWAITING_APPROVAL", critic.verdict());
        assertTrue(critic.involvesFinancialAction());
    }
}
