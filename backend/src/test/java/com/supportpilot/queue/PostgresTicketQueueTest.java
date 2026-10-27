package com.supportpilot.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresTicketQueueTest {

    private QueueJobRepository repository;
    private ObjectMapper objectMapper;
    private PostgresTicketQueue queue;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(QueueJobRepository.class);
        objectMapper = new ObjectMapper();
        queue = new PostgresTicketQueue(repository, objectMapper, 60, 3);
    }

    @Test
    @DisplayName("enqueue saves a PENDING QueueJob with serialized payload")
    void enqueueSavesPendingJob() {
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        queue.enqueue(ticketId, tenantId);

        ArgumentCaptor<QueueJob> captor = ArgumentCaptor.forClass(QueueJob.class);
        verify(repository, times(1)).save(captor.capture());

        QueueJob saved = captor.getValue();
        assertEquals("tickets", saved.getQueueName());
        assertEquals(QueueJob.Status.PENDING, saved.getStatus());
        assertEquals(3, saved.getMaxRetries());
        assertEquals(0, saved.getRetryCount());
        assertTrue(saved.getPayload().contains(ticketId.toString()));
        assertTrue(saved.getPayload().contains(tenantId.toString()));
    }

    @Test
    @DisplayName("poll claims an available job and sets status to PROCESSING with worker id and lockedUntil")
    void pollClaimsAvailableJob() {
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String payload = String.format("{\"ticketId\":\"%s\",\"tenantId\":\"%s\"}", ticketId, tenantId);

        QueueJob job = new QueueJob("tickets", payload, 3);
        job.setId(UUID.randomUUID());

        when(repository.findNextAvailableJobForUpdate("tickets")).thenReturn(Optional.of(job));

        Optional<TicketQueue.QueueJobRecord> result = queue.poll("worker-1");

        assertTrue(result.isPresent());
        assertEquals(job.getId(), result.get().jobId());
        assertEquals(ticketId, result.get().ticketId());
        assertEquals(tenantId, result.get().tenantId());

        assertEquals(QueueJob.Status.PROCESSING, job.getStatus());
        assertEquals("worker-1", job.getLockedBy());
        assertNotNull(job.getLockedUntil());
        verify(repository).save(job);
    }

    @Test
    @DisplayName("ack marks job as COMPLETED")
    void ackMarksJobCompleted() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob();
        job.setId(jobId);
        job.setStatus(QueueJob.Status.PROCESSING);

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        queue.ack(jobId);

        assertEquals(QueueJob.Status.COMPLETED, job.getStatus());
        assertNull(job.getLockedUntil());
        assertNull(job.getLockedBy());
        verify(repository).save(job);
    }

    @Test
    @DisplayName("nack increments retry count and reschedules with backoff")
    void nackReschedulesWithBackoff() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob();
        job.setId(jobId);
        job.setStatus(QueueJob.Status.PROCESSING);
        job.setRetryCount(1);
        job.setMaxRetries(3);

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        queue.nack(jobId, "Upstream timeout");

        assertEquals(2, job.getRetryCount());
        assertEquals(QueueJob.Status.PENDING, job.getStatus());
        assertEquals("Upstream timeout", job.getErrorMessage());
        assertNotNull(job.getRunAt());
        verify(repository).save(job);
    }

    @Test
    @DisplayName("nack moves to DEAD_LETTER when max retries exceeded")
    void nackMovesToDeadLetterWhenMaxRetriesExceeded() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob();
        job.setId(jobId);
        job.setStatus(QueueJob.Status.PROCESSING);
        job.setRetryCount(2);
        job.setMaxRetries(3);

        when(repository.findById(jobId)).thenReturn(Optional.of(job));

        queue.nack(jobId, "Fatal crash");

        assertEquals(3, job.getRetryCount());
        assertEquals(QueueJob.Status.DEAD_LETTER, job.getStatus());
        assertEquals("Fatal crash", job.getErrorMessage());
        verify(repository).save(job);
    }
}
