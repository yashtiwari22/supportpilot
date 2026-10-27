package com.supportpilot.queue;

import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for asynchronous ticket processing queues.
 */
public interface TicketQueue {

    /**
     * Enqueues a ticket processing job.
     */
    void enqueue(UUID ticketId, UUID tenantId);

    /**
     * Attempts to acquire the next available job using visibility timeout.
     */
    Optional<QueueJobRecord> poll(String workerId);

    /**
     * Acknowledges successful job completion.
     */
    void ack(UUID jobId);

    /**
     * Negative acknowledgement with retry or exponential backoff.
     */
    void nack(UUID jobId, String errorMessage);

    /**
     * Moves job directly to dead-letter state.
     */
    void deadLetter(UUID jobId, String reason);

    record QueueJobRecord(
        UUID jobId,
        UUID ticketId,
        UUID tenantId,
        int retryCount
    ) {}
}
