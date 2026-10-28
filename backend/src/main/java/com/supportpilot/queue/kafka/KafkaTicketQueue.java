package com.supportpilot.queue.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.queue.QueueJob;
import com.supportpilot.queue.QueueJobRepository;
import com.supportpilot.queue.TicketQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Apache Kafka implementation of TicketQueue port.
 * Messages are partitioned by tenantId to guarantee in-order processing per tenant.
 */
@Service
@ConditionalOnProperty(name = "supportpilot.queue.type", havingValue = "kafka")
public class KafkaTicketQueue implements TicketQueue {

    private static final Logger log = LoggerFactory.getLogger(KafkaTicketQueue.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final QueueJobRepository queueJobRepository;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final String dlqTopic;

    public KafkaTicketQueue(
            KafkaTemplate<String, String> kafkaTemplate,
            QueueJobRepository queueJobRepository,
            ObjectMapper objectMapper,
            @Value("${supportpilot.queue.kafka.topic-tickets:supportpilot.tickets}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.queueJobRepository = queueJobRepository;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.dlqTopic = topic + ".dlq";
    }

    @Override
    public void enqueue(UUID ticketId, UUID tenantId) {
        UUID jobId = UUID.randomUUID();
        try {
            Map<String, Object> payload = Map.of(
                    "jobId", jobId.toString(),
                    "ticketId", ticketId.toString(),
                    "tenantId", tenantId.toString(),
                    "retryCount", 0,
                    "createdAt", Instant.now().toString()
            );
            String json = objectMapper.writeValueAsString(payload);

            // Also persist tracking record in queue_jobs for unified dashboard DLQ visibility
            QueueJob job = new QueueJob(jobId, "tickets", json, 3);
            queueJobRepository.save(job);

            // Send to Kafka with tenantId as partition key
            kafkaTemplate.send(topic, tenantId.toString(), json);
            log.info("Enqueued ticket job {} to Kafka topic {} (partition key: {})", jobId, topic, tenantId);
        } catch (Exception e) {
            log.error("Failed to publish ticket job to Kafka for ticket {}", ticketId, e);
            throw new RuntimeException("Kafka enqueue failure", e);
        }
    }

    @Override
    public Optional<QueueJobRecord> poll(String workerId) {
        // Kafka uses push-based @KafkaListener rather than poll
        return Optional.empty();
    }

    @Override
    public void ack(UUID jobId) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(QueueJob.Status.COMPLETED);
            job.setUpdatedAt(Instant.now());
            queueJobRepository.save(job);
        });
        log.debug("Acknowledged Kafka ticket job {}", jobId);
    }

    @Override
    public void nack(UUID jobId, String errorMessage) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            int retries = job.getRetryCount() + 1;
            job.setRetryCount(retries);
            job.setErrorMessage(errorMessage);
            job.setUpdatedAt(Instant.now());

            if (retries >= job.getMaxRetries()) {
                deadLetter(jobId, errorMessage);
            } else {
                job.setStatus(QueueJob.Status.PENDING);
                queueJobRepository.save(job);
                log.warn("Nacked Kafka job {} (attempt {}/{}), rescheduled", jobId, retries, job.getMaxRetries());
            }
        });
    }

    @Override
    public void deadLetter(UUID jobId, String reason) {
        queueJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(QueueJob.Status.DEAD_LETTER);
            job.setErrorMessage(reason);
            job.setUpdatedAt(Instant.now());
            queueJobRepository.save(job);

            // Publish to Kafka DLQ topic for external dead-letter stream subscribers
            kafkaTemplate.send(dlqTopic, job.getId().toString(), job.getPayload());
            log.error("Moved Kafka ticket job {} to DLQ: {}", jobId, reason);
        });
    }
}
