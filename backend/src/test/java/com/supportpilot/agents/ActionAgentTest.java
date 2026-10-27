package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.tools.SupportTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ActionAgentTest {

    private SupportTools supportTools;
    private ObjectMapper objectMapper;
    private ActionAgent actionAgent;

    @BeforeEach
    void setUp() {
        supportTools = Mockito.mock(SupportTools.class);
        objectMapper = new ObjectMapper();
        actionAgent = new ActionAgent(supportTools, objectMapper);
    }

    @Test
    @DisplayName("ActionAgent extracts order number and looks up order")
    void actionAgentLooksUpOrder() {
        UUID tenantId = UUID.randomUUID();
        Ticket ticket = new Ticket(
                tenantId,
                "mike@example.com",
                "Mike",
                "Where is my package for order ORD-1042?",
                "Tracking says pending.",
                "idemp-action-1"
        );

        when(supportTools.lookupOrder(eq(tenantId), eq("ORD-1042"), eq("mike@example.com")))
                .thenReturn(Map.of("found", true, "orderNumber", "ORD-1042", "status", "IN_TRANSIT"));

        AgentResult result = actionAgent.run(new AgentContext(ticket));

        assertTrue(result.success());
        assertEquals(AgentName.ACTION, result.agentName());
        assertInstanceOf(ActionAgent.ActionResult.class, result.payload());

        ActionAgent.ActionResult actResult = (ActionAgent.ActionResult) result.payload();
        assertTrue(actResult.actionExecuted());
        assertEquals("ORD-1042", actResult.toolInput().get("orderNumber"));
    }

    @Test
    @DisplayName("ActionAgent evaluates refund eligibility and stages proposal for refund queries")
    void actionAgentProposesRefundForEligibleOrder() {
        UUID tenantId = UUID.randomUUID();
        Ticket ticket = new Ticket(
                tenantId,
                "sarah@example.com",
                "Sarah",
                "Requesting refund for order ORD-1042",
                "Item arrived broken, please issue a full refund.",
                "idemp-action-2"
        );

        when(supportTools.lookupOrder(eq(tenantId), eq("ORD-1042"), eq("sarah@example.com")))
                .thenReturn(Map.of("found", true, "orderNumber", "ORD-1042", "status", "DELIVERED"));

        when(supportTools.checkRefundEligibility(eq(tenantId), eq("ORD-1042")))
                .thenReturn(Map.of("eligible", true, "refundableAmount", new BigDecimal("149.99")));

        when(supportTools.proposeRefund(eq(tenantId), eq("ORD-1042"), any(), any()))
                .thenReturn(Map.of("actionType", "REFUND", "orderNumber", "ORD-1042", "amount", new BigDecimal("149.99")));

        AgentResult result = actionAgent.run(new AgentContext(ticket));
        ActionAgent.ActionResult actResult = (ActionAgent.ActionResult) result.payload();

        assertTrue(actResult.actionExecuted());
        assertNotNull(actResult.proposedAction());
        assertEquals("REFUND", actResult.proposedAction().get("actionType"));
    }
}
