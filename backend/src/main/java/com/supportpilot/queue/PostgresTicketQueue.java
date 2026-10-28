package com.supportpilot.queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL implementation of TicketQueue using SKIP LOCKED row-level locking.
 */
@Service
@ConditionalOnProperty(name = "supportpilot.queue.type", havingValue = "postgres", matchIfMissing = true)
public class PostgresTicketQueue implements TicketQueue {

    private static final Logger log = LoggerFactory.getLogger(PostgresTicketQueue.class);

    private final QueueJobRepository queueJobRepository;
    private final ObjectMapper objectMapper;
    private final int visibilityTimeoutSeconds;
    private final int defaultMaxRetries;

    public PostgresTicketQueue(
            QueueJobRepository queueJobRepository,
            ObjectMapper objectMapper,
            @Value("${supportpilot.queue.visibility-timeout-seconds:60}") int visibilityTimeoutSeconds,
            @Value("${supportpilot.queue.max-retries:3}") int defaultMaxRetries) {
        this.queueJobRepository = queueJobRepository;
        this.objectMapper = objectMapper;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.defaultMaxRetries = defaultMaxRetries;
    }

    @Override
    @Transactional
    public void enqueue(UUID ticketId, UUID tenantId) {
        try {
            Map<String, String> payloadMap = Map.of(
                    "ticketId", ticketId.toString(),
                    "tenantId", tenantId.toString()
            );
            String jsonPayload = objectMapper.writeValueAsString(payloadMap);
            QueueJob job = new QueueJob("tickets", jsonPayload, defaultMaxRetries);
            queueJobRepository.save(job);
            log.debug("Enqueued job {} for ticket {}", job.getId(), ticketId);
        } catch (Exception e) {
            log.error("Failed to enqueue ticket job for ticket {}", ticketId, e);
            throw new RuntimeException("Failed to enqueue job", e);
        }
    }

    @Override
    @Transactional
    public Optional<QueueJobRecord> poll(String workerId) {
        Optional<QueueJob> jobOpt = queueJobRepository.findNextAvailableJobForUpdate("tickets");
        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        QueueJob job = jobOpt.get();
        Instant now = Instant.now();
        job.setStatus(QueueJob.Status.PROCESSING);
        job.setLockedBy(workerId);
        job.setLockedUntil(now.plusSeconds(visibilityTimeoutSeconds));
        queueJobRepository.save(job);

        try {
            Map<String, String> map = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
            UUID ticketId = UUID.fromString(map.get("ticketId"));
            UUID tenantId = UUID.fromString(map.get("tenantId"));
            return Optional.of(new QueueJobRecord(job.getId(), ticketId, tenantId, job.getRetryCount()));
        } catch (Exception e) {
            log.error("Failed to deserialize job payload for job {}", job.getId(), e);
            deadLetter(job.getId(), "Malformed payload: " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public void ack(UUID jobId) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(QueueJob.Status.COMPLETED);
            job.setLockedUntil(null);
            job.setLockedBy(null);
            queueJobRepository.save(job);
            log.debug("Acknowledged job {}", jobId);
        });
    }

    @Override
    @Transactional
    public void nack(UUID jobId, String errorMessage) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            int newRetry = job.getRetryCount() + 1;
            job.setRetryCount(newRetry);
            job.setErrorMessage(errorMessage);
            job.setLockedUntil(null);
            job.setLockedBy(null);

            if (newRetry >= job.getMaxRetries()) {
                job.setStatus(QueueJob.Status.DEAD_LETTER);
                log.warn("Job {} reached max retries ({}), moved to DEAD_LETTER", jobId, job.getMaxRetries());
            } else {
                job.setStatus(QueueJob.Status.PENDING);
                // Exponential/linear backoff: 5s, 10s, 15s...
                job.setRunAt(Instant.now().plusSeconds(newRetry * 5L));
                log.info("Job {} nacked (retry {}/{}), rescheduled in {}s", jobId, newRetry, job.getMaxRetries(), newRetry * 5);
            }
            queueJobRepository.save(job);
        });
    }

    @Override
    @Transactional
    public void deadLetter(UUID jobId, String reason) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(QueueJob.Status.DEAD_LETTER);
            job.setErrorMessage(reason);
            job.setLockedUntil(null);
            job.setLockedBy(null);
            queueJobRepository.save(job);
            log.warn("Job {} forcibly moved to DEAD_LETTER: {}", jobId, reason);
        });
    }
}
