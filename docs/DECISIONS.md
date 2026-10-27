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
