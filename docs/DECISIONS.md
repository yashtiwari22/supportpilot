# Architectural Decision Records (ADR)

## ADR-001: Monorepo Structure & Hexagonal Architecture
- **Status:** Accepted
- **Context:** SupportPilot requires backend services, Next.js console, load testing, evaluation pipelines, and infrastructure setup.
- **Decision:** Use a clean monorepo layout (`backend/`, `console/`, `eval/`, `loadtests/`, `infra/`, `docs/`). Within the backend, follow a Hexagonal (Ports and Adapters) architecture partitioned by domain feature (`tenant`, `auth`, `ticket`, `workflow`, `agents`, `llm`, `queue`, `approval`, `metrics`, `common`).
- **Consequences:** Clean separation of business logic from infrastructure; makes swapping LLM clients (Mock vs Gemini) and queue adapters (Postgres vs Kafka) straightforward with zero domain bleed.

## ADR-002: Default Queue - Postgres `SELECT FOR UPDATE SKIP LOCKED`
- **Status:** Accepted
- **Context:** We need a zero-cost, reliable, transactional queue for ticket ingestion and asynchronous agent processing with visibility timeouts and dead-lettering, with Kafka available for scaled environments.
- **Decision:** Implement `PostgresTicketQueue` using `SELECT ... FOR UPDATE SKIP LOCKED` over `queue_jobs`. Transactional enqueuing guarantees atomicity with ticket persistence.
- **Consequences:** Eliminates external messaging broker overhead for local development and free-tier single-instance deployments, while maintaining an identical contract for Kafka in Phase 3.

## ADR-003: LLM Provider Abstraction & Mock Provider
- **Status:** Accepted
- **Context:** Testing, CI, and local development must run at $0 without requiring external API keys, while supporting deterministic outputs and synthetic latency for load testing.
- **Decision:** Create an `LlmClient` interface with `MockLlmClient` (configurable latency distribution and rule-based deterministic structured triage outputs) and `GeminiLlmClient` (using OpenAI-compatible Gemini endpoints via Spring AI / RestClient).
- **Consequences:** Fast, cost-free local development and robust automated testing in CI.

## ADR-004: State Machine Pattern & Versioned Audit Trail
- **Status:** Accepted
- **Context:** Tickets transition through well-defined lifecycle stages: `RECEIVED` -> `TRIAGED` -> `PROCESSING` -> `DRAFTED` -> `CRITIQUED` -> `(AWAITING_APPROVAL | APPROVED)` -> `SENT`.
- **Decision:** Implement the State Pattern in `workflow`. Any invalid transition throws `IllegalStateTransitionException`. Every transition is persisted to `ticket_events` with from/to states and trigger metadata, and ticket entities use optimistic locking (`@Version`).
- **Consequences:** Total auditability of ticket lifecycles and race-condition immunity under concurrent worker processing.

## ADR-005: Real-time Updates via Server-Sent Events (SSE)
- **Status:** Accepted
- **Context:** Staff console needs live updates without heavy polling or complex WebSocket handshake/proxy setup.
- **Decision:** Use HTTP Server-Sent Events (`GET /api/v1/stream`) with tenant-scoped fanout, proxy buffering disabled (`X-Accel-Buffering: no`, `Cache-Control: no-cache, no-transform`), and `Last-Event-ID` tracking for automatic reconnection.
- **Consequences:** Extremely lightweight, works seamlessly behind reverse proxies (Render, Vercel, Nginx), and connects directly with standard browser `EventSource`.

## ADR-006: Hybrid RAG Search (pgvector Cosine Similarity + Keyword Ranking)
- **Status:** Accepted
- **Context:** Agent resolution requires grounded context from tenant documentation, FAQs, and return policies with minimal hallucination, running entirely on $0 free tier.
- **Decision:** Use PostgreSQL with the `pgvector` extension. Store 768-dimensional document chunk embeddings alongside text. Implement `HybridSearchService` combining cosine distance (`<=>` operator) with full-text search (`tsvector` keyword matching) in a unified score ranking formula.
- **Consequences:** Zero external vector DB subscription costs (e.g., Pinecone/Weaviate), tenant-isolated queries directly via standard SQL, and sub-15ms retrieval latency.

## ADR-007: Critic-Gated Human-in-the-Loop Interception for High-Risk Actions
- **Status:** Accepted
- **Context:** Autonomous support agents must not unilaterally issue irreversible financial refunds or dispatch unverified answers with low confidence.
- **Decision:** Implement a deterministic `CriticAgent` gate that evaluates resolution confidence score, citation grounding, and tool execution proposals. If a refund is staged or the critic confidence score is $< 0.85$, the ticket status is diverted to `AWAITING_APPROVAL` and staged into an `approvals` table.
- **Consequences:** Guaranteed guardrails against financial risk and hallucinated responses, with staff review available via the Console Approval Queue for one-click approval, modification, or rejection.

## ADR-008: Virtual Thread Fan-Out for Parallel Agent Pipelines
- **Status:** Accepted
- **Context:** Resolving a ticket requires RAG knowledge search and tool action lookups simultaneously. Sequential execution doubles response latency.
- **Decision:** Utilize Java 21+ Virtual Threads with `CompletableFuture.supplyAsync` in `WorkflowOrchestrator` to fan out `ResolutionAgent` (retrieval + drafting) and `ActionAgent` (order lookup + eligibility evaluation) concurrently.
- **Consequences:** Wall-clock resolution latency drops to $\max(T_{\text{rag}}, T_{\text{tools}})$, while virtual thread concurrency scales with negligible memory overhead compared to OS threads.

## ADR-009: Pluggable Dual-Queue Architecture (Kafka vs Postgres SKIP LOCKED)
- **Status:** Accepted
- **Context:** Development and test environments require $0 zero-external-broker operation, while scaled production demands distributed partitioning, consumer groups, and horizontal worker scaling.
- **Decision:** Build a Hexagonal `TicketQueue` port with two conditional adapters: `PostgresTicketQueue` (using row-level `SKIP LOCKED` polling, default) and `KafkaTicketQueue` (publishing to partitioned Kafka topics, keyed by `tenantId`). Configured via `supportpilot.queue.type=postgres|kafka`.
- **Consequences:** Zero-broker local bootstrapping and CI testing with zero Docker baggage, with an instant single-line environment flag switch to Kafka for high-throughput deployments.

## ADR-010: Token-Bucket Multi-Tenant Ingestion Rate Limiting
- **Status:** Accepted
- **Context:** In multi-tenant environments, a single rogue client or DDoS ingestion spike can exhaust system resources and starve other tenants.
- **Decision:** Implement an in-memory Token Bucket rate limiter per tenant (`TenantRateLimiter`), enforced via `RateLimitFilter` on ticket ingestion (`POST /api/v1/tickets`). Returns RFC-standard `X-RateLimit-*` and `Retry-After` headers and rejects exceeding requests with HTTP 429.
- **Consequences:** Strict tenant noisy-neighbor isolation and predictable LLM token consumption.

## ADR-011: Poison-Pill Dead-Letter Queue & Idempotent Redrive Pattern
- **Status:** Accepted
- **Context:** Corrupted payloads or jobs that repeatedly fail after maximum retries must not permanently block consumers or be silently discarded.
- **Decision:** Route poisoned and retry-exhausted jobs to a designated `DEAD_LETTER` state and Kafka DLQ topic (`supportpilot.tickets.dlq`). Provide REST endpoints (`/api/v1/queue/redrive/{id}` and `/api/v1/queue/redrive-all`) and an interactive Console UI to inspect errors and redrive jobs safely.
- **Consequences:** Guaranteed message durability, zero unhandled poison pills, and self-service operational recovery.


