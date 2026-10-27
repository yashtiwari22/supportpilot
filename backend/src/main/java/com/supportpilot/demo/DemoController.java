package com.supportpilot.demo;

import com.supportpilot.common.TenantContext;
import com.supportpilot.ticket.TicketResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller providing simulator endpoints to generate sample tickets for demonstrations and evaluations.
 */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private final DemoTicketGeneratorService demoService;
    private static final UUID DEFAULT_ACME_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public DemoController(DemoTicketGeneratorService demoService) {
        this.demoService = demoService;
    }

    public record GenerateTicketsRequest(Integer count, UUID tenantId) {}

    @PostMapping("/generate-tickets")
    public ResponseEntity<List<TicketResponse>> generateTickets(@RequestBody(required = false) GenerateTicketsRequest request) {
        int count = (request != null && request.count() != null) ? request.count() : 3;
        UUID tenantId = (request != null && request.tenantId() != null) ? request.tenantId() : TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = DEFAULT_ACME_TENANT;
        }

        List<TicketResponse> generated = demoService.generateDemoTickets(tenantId, count);
        return ResponseEntity.ok(generated);
    }
}
