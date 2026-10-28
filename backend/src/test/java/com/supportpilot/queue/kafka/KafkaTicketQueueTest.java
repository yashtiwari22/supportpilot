package com.supportpilot.queue.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportpilot.queue.QueueJob;
import com.supportpilot.queue.QueueJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaTicketQueueTest {

    private KafkaTemplate<String, String> kafkaTemplate;
    private QueueJobRepository queueJobRepository;
    private ObjectMapper objectMapper;
    private KafkaTicketQueue kafkaTicketQueue;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);
        queueJobRepository = Mockito.mock(QueueJobRepository.class);
        objectMapper = new ObjectMapper();
        kafkaTicketQueue = new KafkaTicketQueue(
                kafkaTemplate,
                queueJobRepository,
                objectMapper,
                "supportpilot.tickets"
        );
    }

    @Test
    @DisplayName("enqueue sends message with tenantId as partition key and persists queue job tracking")
    void enqueueSendsToKafka() {
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        kafkaTicketQueue.enqueue(ticketId, tenantId);

        verify(kafkaTemplate).send(eq("supportpilot.tickets"), eq(tenantId.toString()), any(String.class));
        verify(queueJobRepository).save(any(QueueJob.class));
    }

    @Test
    @DisplayName("deadLetter updates job to DEAD_LETTER and routes to DLQ topic")
    void deadLetterRoutesToDlqTopic() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob(jobId, "tickets", "{\"ticketId\":\"abc\"}", 3);

        when(queueJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        kafkaTicketQueue.deadLetter(jobId, "Unrecoverable processing failure");

        assertEquals(QueueJob.Status.DEAD_LETTER, job.getStatus());
        assertEquals("Unrecoverable processing failure", job.getErrorMessage());
        verify(queueJobRepository).save(job);
        verify(kafkaTemplate).send(eq("supportpilot.tickets.dlq"), eq(jobId.toString()), eq(job.getPayload()));
    }
}
