package com.supportpilot.queue.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.ticket.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kafka message consumer dispatching ticket jobs onto Java Virtual Threads.
 */
@Component
@ConditionalOnProperty(name = "supportpilot.queue.type", havingValue = "kafka")
public class KafkaQueueConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaQueueConsumer.class);

    private final TicketService ticketService;
    private final KafkaTicketQueue kafkaTicketQueue;
    private final ObjectMapper objectMapper;
    private final ExecutorService virtualThreadExecutor;

    public KafkaQueueConsumer(
            TicketService ticketService,
            KafkaTicketQueue kafkaTicketQueue,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.kafkaTicketQueue = kafkaTicketQueue;
        this.objectMapper = objectMapper;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @KafkaListener(
            topics = "${supportpilot.queue.kafka.topic-tickets:supportpilot.tickets}",
            groupId = "${supportpilot.queue.kafka.group-id:supportpilot-workers}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String message) {
        virtualThreadExecutor.submit(() -> processMessage(message));
    }

    private void processMessage(String message) {
        UUID jobId = null;
        UUID ticketId = null;
        UUID tenantId = null;

        try {
            Map<String, Object> map = objectMapper.readValue(message, new TypeReference<>() {});
            jobId = UUID.fromString(map.get("jobId").toString());
            ticketId = UUID.fromString(map.get("ticketId").toString());
            tenantId = UUID.fromString(map.get("tenantId").toString());
        } catch (Exception e) {
            log.error("Poison pill detected on Kafka topic, unable to parse message: {}", message, e);
            // Move unparseable message directly to DLQ
            kafkaTicketQueue.deadLetter(UUID.randomUUID(), "Poison pill: unparseable message format - " + e.getMessage());
            return;
        }

        try {
            log.info("Processing ticket {} from Kafka (jobId: {})", ticketId, jobId);
            ticketService.processTriageStep(ticketId, tenantId);
            kafkaTicketQueue.ack(jobId);
        } catch (Exception e) {
            log.error("Failed processing ticket {} from Kafka (jobId: {})", ticketId, jobId, e);
            kafkaTicketQueue.nack(jobId, e.getMessage());
        }
    }
}
