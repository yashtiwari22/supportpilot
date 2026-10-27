package com.supportpilot.tools;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an e-commerce order used by ActionAgent tool calling.
 */
@Entity
@Table(name = "demo_orders")
public class DemoOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String items;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 10)
    private String currency = "USD";

    @Column(nullable = false, length = 30)
    private String status; // DELIVERED, IN_TRANSIT, CANCELLED, REFUNDED, PENDING

    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DemoOrder() {}

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
