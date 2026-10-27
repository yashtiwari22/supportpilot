package com.supportpilot.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.ticket.Ticket;
import com.supportpilot.tools.SupportTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Agent responsible for business tool execution against tenant databases (orders, refunds, cancellations).
 */
@Component
public class ActionAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ActionAgent.class);
    private static final Pattern ORDER_PATTERN = Pattern.compile("(?i)(ORD-\\d+|#?\\d{4,6})");

    private final SupportTools supportTools;
    private final ObjectMapper objectMapper;

    public record ActionResult(
            boolean actionExecuted,
            String toolName,
            Map<String, Object> toolInput,
            Map<String, Object> toolOutput,
            Map<String, Object> proposedAction
    ) {}

    public ActionAgent(SupportTools supportTools, ObjectMapper objectMapper) {
        this.supportTools = supportTools;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentName name() {
        return AgentName.ACTION;
    }

    @Override
    public AgentResult run(AgentContext context) {
        Ticket ticket = context.ticket();
        long startTime = System.currentTimeMillis();

        String combinedText = ticket.getSubject() + " " + ticket.getBody();
        String orderNumber = extractOrderNumber(combinedText);

        boolean isRefundQuery = combinedText.toLowerCase().contains("refund")
                || combinedText.toLowerCase().contains("return")
                || combinedText.toLowerCase().contains("cancel");

        Map<String, Object> toolInput = new HashMap<>();
        Map<String, Object> toolOutput = new HashMap<>();
        Map<String, Object> proposedAction = null;
        String toolName = "none";
        boolean actionExecuted = false;

        if (orderNumber != null) {
            toolName = "lookupOrder";
            toolInput.put("orderNumber", orderNumber);
            toolInput.put("customerEmail", ticket.getCustomerEmail());
            Map<String, Object> lookupRes = supportTools.lookupOrder(ticket.getTenantId(), orderNumber, ticket.getCustomerEmail());
            toolOutput = lookupRes != null ? new java.util.HashMap<>(lookupRes) : new java.util.HashMap<>();
            actionExecuted = true;

            if (isRefundQuery && Boolean.TRUE.equals(toolOutput.get("found"))) {
                toolName = "checkRefundEligibilityAndPropose";
                var eligibility = supportTools.checkRefundEligibility(ticket.getTenantId(), orderNumber);
                toolOutput.put("eligibility", eligibility);

                if (Boolean.TRUE.equals(eligibility.get("eligible"))) {
                    BigDecimal amount = (BigDecimal) eligibility.get("refundableAmount");
                    proposedAction = supportTools.proposeRefund(
                            ticket.getTenantId(),
                            orderNumber,
                            amount,
                            "Customer requested return within 30-day window"
                    );
                }
            }
        }

        ActionResult actionResult = new ActionResult(actionExecuted, toolName, toolInput, toolOutput, proposedAction);
        long durationMs = System.currentTimeMillis() - startTime;

        return new AgentResult(
                name(),
                true,
                "deterministic-action-engine",
                85,
                60,
                BigDecimal.valueOf(0.000050),
                durationMs,
                actionResult,
                null,
                Map.of(
                        "actionExecuted", actionExecuted,
                        "toolName", toolName,
                        "hasProposedAction", proposedAction != null
                )
        );
    }

    private String extractOrderNumber(String text) {
        Matcher matcher = ORDER_PATTERN.matcher(text);
        if (matcher.find()) {
            String match = matcher.group(1);
            if (!match.toUpperCase().startsWith("ORD-")) {
                if (match.startsWith("#")) match = match.substring(1);
                match = "ORD-" + match;
            }
            return match.toUpperCase();
        }
        return null;
    }
}
