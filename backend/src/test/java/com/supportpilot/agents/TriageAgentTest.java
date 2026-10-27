package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.llm.LlmClient;
import com.supportpilot.llm.LlmRequest;
import com.supportpilot.llm.LlmResponse;
import com.supportpilot.llm.MockLlmClient;
import com.supportpilot.ticket.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TriageAgentTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("TriageAgent parses valid JSON output into TriageResult")
    void triageParsesValidJson() {
        LlmClient mockLlm = Mockito.mock(LlmClient.class);
        String json = """
        {
          "category": "BILLING",
          "priority": "HIGH",
          "sentiment": "NEGATIVE",
          "summary": "Customer asking for refund on order 1042",
          "routeToResolutionAgent": true,
          "routeToActionAgent": true
        }
        """;
        when(mockLlm.chat(any(LlmRequest.class))).thenReturn(new LlmResponse(
                json, "mock-gemini", 120, 45, new BigDecimal("0.0001"), 80
        ));

        TriageAgent agent = new TriageAgent(mockLlm, objectMapper);
        Ticket ticket = new Ticket(
                UUID.randomUUID(),
                "customer@example.com",
                "Customer",
                "Refund my money",
                "I want a refund for my order",
                "idemp-1"
        );

        AgentResult result = agent.run(new AgentContext(ticket));

        assertTrue(result.success());
        assertEquals(AgentName.TRIAGE, result.agentName());
        assertInstanceOf(TriageResult.class, result.payload());

        TriageResult triageResult = (TriageResult) result.payload();
        assertEquals("BILLING", triageResult.category());
        assertEquals("HIGH", triageResult.priority());
        assertEquals("NEGATIVE", triageResult.sentiment());
        assertTrue(triageResult.routeToActionAgent());
    }

    @Test
    @DisplayName("TriageAgent handles MockLlmClient end to end")
    void triageWithRealMockLlmClient() {
        MockLlmClient mockLlm = new MockLlmClient(0, 0, 0.0);
        TriageAgent agent = new TriageAgent(mockLlm, objectMapper);

        Ticket ticket = new Ticket(
                UUID.randomUUID(),
                "test@example.com",
                "Alice",
                "Order #ORD-1042 delayed",
                "Where is my package? The tracking has not updated.",
                "idemp-2"
        );

        AgentResult result = agent.run(new AgentContext(ticket));
        assertTrue(result.success());
        assertNotNull(result.payload());

        TriageResult triage = (TriageResult) result.payload();
        assertEquals("ORDER_INQUIRY", triage.category());
        assertTrue(triage.priority().equals("HIGH") || triage.priority().equals("MEDIUM"));
    }

    @Test
    @DisplayName("TriageAgent falls back safely when LLM produces malformed output")
    void triageFallsBackOnMalformedOutput() {
        LlmClient mockLlm = Mockito.mock(LlmClient.class);
        when(mockLlm.chat(any(LlmRequest.class))).thenReturn(new LlmResponse(
                "I am not a JSON object!", "mock-gemini", 50, 10, BigDecimal.ZERO, 50
        ));

        TriageAgent agent = new TriageAgent(mockLlm, objectMapper);
        Ticket ticket = new Ticket(
                UUID.randomUUID(),
                "test@example.com",
                "Bob",
                "Hello",
                "Just saying hi",
                "idemp-3"
        );

        AgentResult result = agent.run(new AgentContext(ticket));
        assertTrue(result.success());
        assertInstanceOf(TriageResult.class, result.payload());

        TriageResult fallback = (TriageResult) result.payload();
        assertEquals("GENERAL", fallback.category());
        assertEquals("MEDIUM", fallback.priority());
    }
}
