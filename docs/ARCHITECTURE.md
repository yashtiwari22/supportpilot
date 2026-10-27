# SupportPilot Architecture

SupportPilot is an event-driven, multi-tenant AI customer support platform built on Hexagonal Architecture (Ports and Adapters).

## 1. System Overview

```mermaid
flowchart TD
    Customer([Customer Ingestion]) -->|POST /api/v1/tickets + Idempotency-Key| IngestionCtrl[Ticket Ingestion Controller]
    IngestionCtrl -->|API Key Auth & Tenant Context| TenantFilter[Auth Layer]
    TenantFilter --> TicketService[Ticket Service]
    TicketService -->|1. Save Ticket| TicketDB[(PostgreSQL)]
    TicketService -->|2. Record Event| TicketEventsDB[(Ticket Events)]
    TicketService -->|3. Enqueue Job| Queue[(Postgres Queue)]
    
    QueueWorker[Virtual Thread Queue Worker] -->|SELECT FOR UPDATE SKIP LOCKED| Queue
    QueueWorker --> WorkflowOrchestrator[Workflow Orchestrator]
    
    WorkflowOrchestrator --> TriageAgent[Triage Agent]
    TriageAgent --> LlmClient[LlmClient Interface]
    LlmClient -.-> MockLlm[MockLlmClient]
    LlmClient -.-> GeminiLlm[GeminiLlmClient]
    
    WorkflowOrchestrator -->|State Change Event| SseHub[SSE Event Hub]
    SseHub -->|Server-Sent Events| Console[Next.js Staff Console]
```

## 2. Ticket Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> RECEIVED: Ingestion
    RECEIVED --> TRIAGED: TriageAgent
    TRIAGED --> PROCESSING: Fan-out Agents
    PROCESSING --> DRAFTED: Resolution & Action Complete
    DRAFTED --> CRITIQUED: CriticAgent
    CRITIQUED --> REVISING: Critic Requested Changes (< 2 loops)
    REVISING --> DRAFTED: Re-draft
    CRITIQUED --> AWAITING_APPROVAL: High Risk / Financial Action / Low Confidence
    CRITIQUED --> APPROVED: Low Risk Auto-Approved
    AWAITING_APPROVAL --> APPROVED: Human Approval
    AWAITING_APPROVAL --> REJECTED: Human Rejection
    APPROVED --> SENT: Dispatch Reply
    PROCESSING --> FAILED: Unrecoverable Error
    FAILED --> DEAD_LETTER: Max Retries Exceeded
```

## 3. Concurrency Rationale

1. **Java 21/25 Virtual Threads for I/O & Queue Workers:**
   Ingestion and LLM waiting are I/O bound. Using virtual threads per task (`Executors.newVirtualThreadPerTaskExecutor()`) allows thousands of concurrent operations with negligible memory overhead compared to platform threads (~2KB vs ~1MB).
2. **Optimistic Locking (`@Version`):**
   `tickets` table has a `version` column. When concurrent updates or human reviews occur, Spring Data JPA prevents lost updates without table-level database locking.
3. **Queue Concurrency via `SKIP LOCKED`:**
   `PostgresTicketQueue` uses `SELECT ... FOR UPDATE SKIP LOCKED` with a configurable concurrency limit. Workers never block each other waiting on the same row, eliminating lock contention.
4. **Idempotency Guarantee:**
   Ingestion requires an `Idempotency-Key` header. Duplicate requests within a 24-hour window return the existing ticket immediately without re-enqueuing.
