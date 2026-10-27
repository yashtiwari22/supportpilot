package com.supportpilot.queue;

import com.supportpilot.ticket.TicketService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background consumer leveraging Java 21/25 Virtual Threads to process queued ticket jobs.
 */
@Component
public class QueueWorker {

    private static final Logger log = LoggerFactory.getLogger(QueueWorker.class);

    private final TicketQueue ticketQueue;
    private final TicketService ticketService;
    private final int concurrency;
    private final long pollIntervalMs;
    private final String workerId;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService virtualThreadExecutor;
    private Thread pollerThread;
    private Semaphore concurrencySemaphore;

    public QueueWorker(
            TicketQueue ticketQueue,
            TicketService ticketService,
            @Value("${supportpilot.queue.concurrency:4}") int concurrency,
            @Value("${supportpilot.queue.poll-interval-ms:500}") long pollIntervalMs) {
        this.ticketQueue = ticketQueue;
        this.ticketService = ticketService;
        this.concurrency = concurrency;
        this.pollIntervalMs = pollIntervalMs;
        this.workerId = initWorkerId();
    }

    private String initWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "worker-" + ProcessHandle.current().pid();
        }
    }

    @PostConstruct
    public void start() {
        running.set(true);
        concurrencySemaphore = new Semaphore(concurrency);
        virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        pollerThread = Thread.ofPlatform().name("queue-poller-main").start(this::pollLoop);
        log.info("Started QueueWorker [{}] with concurrency {} using Virtual Threads", workerId, concurrency);
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                // Wait for an available concurrency slot before polling
                concurrencySemaphore.acquire();

                Optional<TicketQueue.QueueJobRecord> jobOpt = ticketQueue.poll(workerId);
                if (jobOpt.isPresent()) {
                    TicketQueue.QueueJobRecord job = jobOpt.get();
                    virtualThreadExecutor.submit(() -> processJob(job));
                } else {
                    // No job found, release slot and back off slightly
                    concurrencySemaphore.release();
                    Thread.sleep(pollIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in queue poller loop", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processJob(TicketQueue.QueueJobRecord job) {
        try {
            log.debug("Worker [{}] processing ticket job {} (retry #{})", workerId, job.jobId(), job.retryCount());
            ticketService.processTriageStep(job.ticketId(), job.tenantId());
            ticketQueue.ack(job.jobId());
        } catch (Exception ex) {
            log.error("Worker [{}] failed to process job {} for ticket {}", workerId, job.jobId(), job.ticketId(), ex);
            ticketQueue.nack(job.jobId(), ex.getMessage());
        } finally {
            concurrencySemaphore.release();
        }
    }

    @PreDestroy
    public void stop() {
        log.info("Stopping QueueWorker [{}]...", workerId);
        running.set(false);
        if (pollerThread != null) {
            pollerThread.interrupt();
        }
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdown();
        }
    }
}
