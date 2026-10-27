package com.supportpilot.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Suite of tools executable by ActionAgent against tenant business records.
 */
@Service
public class SupportTools {

    private static final Logger log = LoggerFactory.getLogger(SupportTools.class);
    private final DemoOrderRepository orderRepository;

    public SupportTools(DemoOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Looks up order details by order number or customer email.
     */
    public Map<String, Object> lookupOrder(UUID tenantId, String orderNumber, String customerEmail) {
        log.info("Tool execution: lookupOrder(tenant={}, order={}, email={})", tenantId, orderNumber, customerEmail);

        Optional<DemoOrder> orderOpt = Optional.empty();
        if (orderNumber != null && !orderNumber.isBlank()) {
            orderOpt = orderRepository.findByTenantIdAndOrderNumber(tenantId, orderNumber.trim());
        }
        if (orderOpt.isEmpty() && customerEmail != null && !customerEmail.isBlank()) {
            var orders = orderRepository.findByTenantIdAndCustomerEmail(tenantId, customerEmail.trim());
            if (!orders.isEmpty()) {
                orderOpt = Optional.of(orders.get(0));
            }
        }

        if (orderOpt.isEmpty()) {
            return Map.of("found", false, "message", "No order found matching details");
        }

        DemoOrder order = orderOpt.get();
        return Map.of(
                "found", true,
                "orderNumber", order.getOrderNumber(),
                "status", order.getStatus(),
                "totalAmount", order.getTotalAmount(),
                "currency", order.getCurrency(),
                "orderedAt", order.getOrderedAt().toString(),
                "items", order.getItems()
        );
    }

    /**
     * Evaluates order status and return policy (30-day window) to determine refund eligibility.
     */
    public Map<String, Object> checkRefundEligibility(UUID tenantId, String orderNumber) {
        log.info("Tool execution: checkRefundEligibility(tenant={}, order={})", tenantId, orderNumber);

        Optional<DemoOrder> orderOpt = orderRepository.findByTenantIdAndOrderNumber(tenantId, orderNumber.trim());
        if (orderOpt.isEmpty()) {
            return Map.of("eligible", false, "reason", "Order not found");
        }

        DemoOrder order = orderOpt.get();
        long daysSinceOrder = Duration.between(order.getOrderedAt(), Instant.now()).toDays();

        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            return Map.of("eligible", false, "reason", "Order has already been refunded.");
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            return Map.of("eligible", false, "reason", "Order was cancelled prior to shipment.");
        }
        if (daysSinceOrder > 30) {
            return Map.of(
                    "eligible", false,
                    "reason", String.format("Order was placed %d days ago, exceeding the 30-day return policy.", daysSinceOrder)
            );
        }

        return Map.of(
                "eligible", true,
                "daysSinceOrder", daysSinceOrder,
                "refundableAmount", order.getTotalAmount(),
                "currency", order.getCurrency(),
                "requiresReturn", "DELIVERED".equalsIgnoreCase(order.getStatus())
        );
    }

    /**
     * Staged proposal for a refund, requiring human approval before dispatching funds.
     */
    public Map<String, Object> proposeRefund(UUID tenantId, String orderNumber, BigDecimal amount, String reason) {
        log.info("Tool execution: proposeRefund(tenant={}, order={}, amount={}, reason={})", tenantId, orderNumber, amount, reason);

        return Map.of(
                "actionType", "REFUND",
                "orderNumber", orderNumber,
                "amount", amount,
                "reason", reason,
                "status", "PROPOSED_PENDING_APPROVAL",
                "timestamp", Instant.now().toString()
        );
    }
}
