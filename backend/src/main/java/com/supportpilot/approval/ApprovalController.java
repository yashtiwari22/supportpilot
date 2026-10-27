package com.supportpilot.approval;

import com.supportpilot.common.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller exposing human review actions on the approval queue.
 */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    public record ApproveRequest(String notes) {}
    public record EditAndApproveRequest(String editedReply, String notes) {}
    public record RejectRequest(String reason) {}

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public ResponseEntity<List<ApprovalService.ApprovalDto>> listPending() {
        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(approvalService.listPendingApprovals(tenantId));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @RequestBody(required = false) ApproveRequest request,
            Authentication auth) {
        UUID reviewerId = auth != null && auth.getName() != null && !auth.getName().startsWith("api-key:")
                ? UUID.fromString(auth.getName()) : null;
        approvalService.approve(id, reviewerId, request != null ? request.notes() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/edit-and-approve")
    public ResponseEntity<Void> editAndApprove(
            @PathVariable UUID id,
            @RequestBody EditAndApproveRequest request,
            Authentication auth) {
        UUID reviewerId = auth != null && auth.getName() != null && !auth.getName().startsWith("api-key:")
                ? UUID.fromString(auth.getName()) : null;
        approvalService.editAndApprove(id, reviewerId, request.editedReply(), request.notes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID id,
            @RequestBody(required = false) RejectRequest request,
            Authentication auth) {
        UUID reviewerId = auth != null && auth.getName() != null && !auth.getName().startsWith("api-key:")
                ? UUID.fromString(auth.getName()) : null;
        approvalService.reject(id, reviewerId, request != null ? request.reason() : "Rejected by staff reviewer");
        return ResponseEntity.ok().build();
    }
}
