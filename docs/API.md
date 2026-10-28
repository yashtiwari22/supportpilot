# SupportPilot API Reference

## Authentication

### 1. Ingestion API Key
Used by external ticketing systems or webhook integrations.
- **Header:** `X-API-Key: <api_key>`
- **Example Key (Demo):** `sp_live_acme_demo_key_12345`

### 2. Console JWT
Used by staff members accessing the Next.js console.
- **Header:** `Authorization: Bearer <jwt_token>`

---

## Endpoints

### Ingestion

#### `POST /api/v1/tickets`
Create and enqueue a new customer ticket.
- **Headers:**
  - `X-API-Key: <api_key>`
  - `Idempotency-Key: <unique-uuid>`
- **Request Body:**
```json
{
  "customerEmail": "sarah.smith@example.com",
  "customerName": "Sarah Smith",
  "subject": "Order #1042 not delivered yet",
  "body": "Hi, I placed order #1042 five days ago and it still hasn't arrived. Can you check?"
}
```
- **Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "tenantId": "00000000-0000-0000-0000-000000000001",
  "customerEmail": "sarah.smith@example.com",
  "customerName": "Sarah Smith",
  "subject": "Order #1042 not delivered yet",
  "body": "Hi, I placed order #1042 five days ago and it still hasn't arrived. Can you check?",
  "status": "RECEIVED",
  "category": null,
  "priority": null,
  "sentiment": null,
  "createdAt": "2026-10-27T16:00:00Z"
}
```

---

### Console Auth

#### `POST /api/v1/auth/login`
- **Request Body:**
```json
{
  "email": "admin@acme.com",
  "password": "password123"
}
```
- **Response (200 OK):**
```json
{
  "token": "eyJhbGciOi...",
  "user": {
    "id": "...",
    "email": "admin@acme.com",
    "fullName": "Acme Admin",
    "role": "ADMIN",
    "tenantId": "00000000-0000-0000-0000-000000000001",
    "tenantName": "Acme Electronics"
  }
}
```

#### `GET /api/v1/auth/me`
- **Headers:** `Authorization: Bearer <jwt_token>`
- **Response (200 OK):** User details object.

---

### Console Tickets

#### `GET /api/v1/tickets`
List tickets for current tenant. Supports query filters: `status`, `priority`, `category`, `search`.

#### `GET /api/v1/tickets/{id}`
Retrieve ticket details by ID.

#### `GET /api/v1/tickets/{id}/events`
Retrieve audit events for the ticket.

#### `GET /api/v1/tickets/{id}/agent-runs`
Retrieve AI agent execution records for the ticket.

---

### Simulator

#### `POST /api/v1/demo/generate-tickets`
Generate N demo tickets for testing.
- **Request Body:**
```json
{
  "count": 3
}
```
- **Response (200 OK):** List of generated tickets.

---

### Real-Time Streaming (SSE)

#### `GET /api/v1/stream`
Server-Sent Events connection for real-time ticket board and details updates.
- **Headers:**
  - `Authorization: Bearer <jwt_token>` (or `?token=<jwt_token>`)
  - Optional: `Last-Event-ID: <event-id>`
- **Events Emitted:**
  - `ticket_created`
  - `ticket_state_changed`
  - `agent_step_completed`
  - `approval_requested`
  - `done`

---

### Human-in-the-Loop Approvals

#### `GET /api/v1/approvals`
List pending human approvals for the current tenant.

#### `POST /api/v1/approvals/{id}/approve`
One-click approve proposed reply and dispatch to customer.
- **Request Body (Optional):** `{"notes": "Looks good"}`

#### `POST /api/v1/approvals/{id}/edit-and-approve`
Modify draft reply and dispatch to customer.
- **Request Body:** `{"editedReply": "...", "notes": "..."}`

#### `POST /api/v1/approvals/{id}/reject`
Reject proposed draft and transition ticket to `REJECTED`.
- **Request Body (Optional):** `{"reason": "Outside return window"}`

---

### Knowledge Base & RAG Index

#### `GET /api/v1/kb/articles`
List knowledge base articles with pgvector chunk counts.

#### `POST /api/v1/kb/articles`
Create article and immediately generate 768-dim vector embeddings in pgvector.
- **Request Body:** `{"title": "...", "content": "..."}`

#### `DELETE /api/v1/kb/articles/{id}`
Delete article and cascade delete vector embeddings.

---

### Queue & Dead-Letter (DLQ) Management

#### `GET /api/v1/queue/dead-letters`
List dead-lettered background jobs with failure reasons and retry counts.

#### `GET /api/v1/queue/stats`
Retrieve queue statistics (e.g., current dead-letter count).

#### `POST /api/v1/queue/redrive/{jobId}`
Redrive an individual dead-lettered job back to `PENDING` status.

#### `POST /api/v1/queue/redrive-all`
Redrive all dead-lettered jobs in batch.

---

### Observability & Metrics

#### `GET /actuator/prometheus`
Prometheus metrics scrape target including:
- `supportpilot_tickets_ingested_total`
- `supportpilot_tickets_resolved_total`
- `supportpilot_agent_duration_seconds`
- `supportpilot_critic_interceptions_total`
- `supportpilot_ratelimit_rejected_total`
- `supportpilot_queue_dlq_size`

