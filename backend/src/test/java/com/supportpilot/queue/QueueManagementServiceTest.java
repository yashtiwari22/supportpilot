package com.supportpilot.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueueManagementServiceTest {

    private QueueJobRepository queueJobRepository;
    private ObjectMapper objectMapper;
    private QueueManagementService service;

    @BeforeEach
    void setUp() {
        queueJobRepository = Mockito.mock(QueueJobRepository.class);
        objectMapper = new ObjectMapper();
        service = new QueueManagementService(queueJobRepository, objectMapper);
    }

    @Test
    @DisplayName("Lists dead-letter jobs with parsed ticket and tenant IDs")
    void listsDeadLetters() {
        UUID jobId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String payload = String.format("{\"ticketId\":\"%s\",\"tenantId\":\"%s\"}", ticketId, tenantId);
        QueueJob job = new QueueJob(jobId, "tickets", payload, 3);
        job.setStatus(QueueJob.Status.DEAD_LETTER);
        job.setErrorMessage("Max retries exceeded");

        when(queueJobRepository.findTop100ByStatusOrderByCreatedAtDesc(QueueJob.Status.DEAD_LETTER))
                .thenReturn(List.of(job));

        List<QueueManagementService.DeadLetterJobDto> result = service.listDeadLetters();

        assertEquals(1, result.size());
        assertEquals(jobId, result.get(0).id());
        assertEquals(ticketId, result.get(0).ticketId());
        assertEquals(tenantId, result.get(0).tenantId());
        assertEquals("Max retries exceeded", result.get(0).errorMessage());
    }

    @Test
    @DisplayName("Redrive resets dead-letter job to PENDING with 0 retries and cleared error")
    void redriveResetsJobToPending() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob(jobId, "tickets", "{}", 3);
        job.setStatus(QueueJob.Status.DEAD_LETTER);
        job.setRetryCount(3);
        job.setErrorMessage("Poison pill");

        when(queueJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        boolean redrived = service.redriveJob(jobId);

        assertTrue(redrived);
        assertEquals(QueueJob.Status.PENDING, job.getStatus());
        assertEquals(0, job.getRetryCount());
        assertNull(job.getErrorMessage());
        assertNull(job.getLockedBy());
        verify(queueJobRepository).save(job);
    }

    @Test
    @DisplayName("Redrive fails if job is already COMPLETED or not found")
    void redriveFailsForCompletedJob() {
        UUID jobId = UUID.randomUUID();
        QueueJob job = new QueueJob(jobId, "tickets", "{}", 3);
        job.setStatus(QueueJob.Status.COMPLETED);

        when(queueJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        boolean redrived = service.redriveJob(jobId);

        assertFalse(redrived);
        assertEquals(QueueJob.Status.COMPLETED, job.getStatus());
        verify(queueJobRepository, never()).save(any());
    }
}
