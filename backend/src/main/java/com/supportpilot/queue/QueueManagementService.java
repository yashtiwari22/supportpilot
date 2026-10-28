package com.supportpilot.queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service managing dead-letter inspection, poison pill analysis, and job redriving.
 */
@Service
public class QueueManagementService {

    private static final Logger log = LoggerFactory.getLogger(QueueManagementService.class);

    private final QueueJobRepository queueJobRepository;
    private final ObjectMapper objectMapper;

    public record DeadLetterJobDto(
            UUID id,
            String queueName,
            UUID ticketId,
            UUID tenantId,
            int retryCount,
            int maxRetries,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public QueueManagementService(QueueJobRepository queueJobRepository, ObjectMapper objectMapper) {
        this.queueJobRepository = queueJobRepository;
        this.objectMapper = objectMapper;
    }

    public List<DeadLetterJobDto> listDeadLetters() {
        return queueJobRepository.findTop100ByStatusOrderByCreatedAtDesc(QueueJob.Status.DEAD_LETTER)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public long getDeadLetterCount() {
        return queueJobRepository.countByStatus(QueueJob.Status.DEAD_LETTER);
    }

    @Transactional
    public boolean redriveJob(UUID jobId) {
        Optional<QueueJob> opt = queueJobRepository.findById(jobId);
        if (opt.isEmpty()) {
            return false;
        }

        QueueJob job = opt.get();
        if (job.getStatus() != QueueJob.Status.DEAD_LETTER && job.getStatus() != QueueJob.Status.FAILED) {
            log.warn("Job {} is not in DEAD_LETTER or FAILED status (current: {})", jobId, job.getStatus());
            return false;
        }

        job.setStatus(QueueJob.Status.PENDING);
        job.setRetryCount(0);
        job.setRunAt(Instant.now());
        job.setLockedBy(null);
        job.setLockedUntil(null);
        job.setErrorMessage(null);
        job.setUpdatedAt(Instant.now());
        queueJobRepository.save(job);

        log.info("Redrived dead-letter job {}", jobId);
        return true;
    }

    @Transactional
    public int redriveAll() {
        List<QueueJob> deadLetters = queueJobRepository.findTop100ByStatusOrderByCreatedAtDesc(QueueJob.Status.DEAD_LETTER);
        int count = 0;
        for (QueueJob job : deadLetters) {
            job.setStatus(QueueJob.Status.PENDING);
            job.setRetryCount(0);
            job.setRunAt(Instant.now());
            job.setLockedBy(null);
            job.setLockedUntil(null);
            job.setErrorMessage(null);
            job.setUpdatedAt(Instant.now());
            queueJobRepository.save(job);
            count++;
        }
        log.info("Redrived {} dead-letter jobs in batch", count);
        return count;
    }

    private DeadLetterJobDto toDto(QueueJob job) {
        UUID ticketId = null;
        UUID tenantId = null;
        try {
            Map<String, Object> map = objectMapper.readValue(job.getPayload(), new TypeReference<>() {});
            if (map.get("ticketId") != null) {
                ticketId = UUID.fromString(map.get("ticketId").toString());
            }
            if (map.get("tenantId") != null) {
                tenantId = UUID.fromString(map.get("tenantId").toString());
            }
        } catch (Exception e) {
            log.debug("Could not parse payload for job {}", job.getId());
        }

        return new DeadLetterJobDto(
                job.getId(),
                job.getQueueName(),
                ticketId,
                tenantId,
                job.getRetryCount(),
                job.getMaxRetries(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }
}
