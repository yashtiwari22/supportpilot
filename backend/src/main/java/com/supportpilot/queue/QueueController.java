package com.supportpilot.queue;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller exposing endpoints for DLQ monitoring, queue metrics, and poison-pill job redriving.
 */
@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final QueueManagementService queueManagementService;

    public QueueController(QueueManagementService queueManagementService) {
        this.queueManagementService = queueManagementService;
    }

    @GetMapping("/dead-letters")
    public ResponseEntity<List<QueueManagementService.DeadLetterJobDto>> listDeadLetters() {
        return ResponseEntity.ok(queueManagementService.listDeadLetters());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "deadLetterCount", queueManagementService.getDeadLetterCount()
        ));
    }

    @PostMapping("/redrive/{jobId}")
    public ResponseEntity<Map<String, Object>> redriveJob(@PathVariable UUID jobId) {
        boolean success = queueManagementService.redriveJob(jobId);
        if (success) {
            return ResponseEntity.ok(Map.of("success", true, "jobId", jobId, "message", "Job redrived to PENDING status"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "jobId", jobId, "message", "Job not found or not in eligible DLQ status"));
        }
    }

    @PostMapping("/redrive-all")
    public ResponseEntity<Map<String, Object>> redriveAll() {
        int count = queueManagementService.redriveAll();
        return ResponseEntity.ok(Map.of("success", true, "redrivedCount", count));
    }
}
