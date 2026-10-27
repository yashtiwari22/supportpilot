# SupportPilot

**SupportPilot** is an event-driven, multi-tenant AI customer support platform built on Hexagonal Architecture (Ports and Adapters). It orchestrates specialized AI agents (Triage, Resolution with RAG, Action with Tool Calling, and Critic with reflection loops) alongside human-in-the-loop review.

---

## Architecture Overview

- **Backend:** Java 21, Spring Boot 3.4.x, PostgreSQL with pgvector, Flyway, Java Virtual Threads, State Pattern state machine, Postgres `SELECT FOR UPDATE SKIP LOCKED` transactional queue, Server-Sent Events (SSE).
- **Frontend Console:** Next.js (App Router), React 19, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, Lucide icons, dark mode default.
- **Infrastructure:** Docker Compose (PostgreSQL 16 + pgvector).

Detailed diagrams and concurrency decisions can be found in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/DECISIONS.md](docs/DECISIONS.md).

---

## Quickstart & Verification (Phase 1)

### 1. Prerequisites
- Docker & Docker Compose
- Java 21+ (`openjdk 21` or `openjdk 25`)
- Node.js 18+ and npm

### 2. Start PostgreSQL with pgvector
```bash
docker compose -f infra/docker-compose.yml up -d
```

### 3. Run Backend Service
```bash
cd backend
./mvnw clean spring-boot:run
```
The backend starts on port `8080`. Flyway automatically creates the database schema (`V1`) and seeds demo tenants, API keys, staff users, demo orders, and knowledge base articles (`V2`).

To run backend tests:
```bash
./mvnw test
```

### 4. Run Staff Console
In a new terminal:
```bash
cd console
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) in your browser.

---

## Demo Verification Walkthrough

1. **Sign In to Console:**
   - Navigate to [http://localhost:3000](http://localhost:3000).
   - Click the **"Acme Electronics Admin"** quick-fill button (`admin@acme.com` / `password123`) and click **Sign In**.
2. **Observe Live Kanban Board:**
   - You will see the Live Board with real-time SSE connection indicator (**SSE: Connected**).
3. **Simulate Ingestion Tickets:**
   - Click the purple **"Simulate Tickets"** button in the header.
   - Select **3 Tickets** and click **Run Simulator**.
   - Watch the tickets arrive in **Ingested** and move to **AI Triaged** in real time as the virtual-thread queue worker processes and classifies them.
4. **Inspect Ticket Intelligence & Audit Timeline:**
   - Click any ticket card to open the **Ticket Detail Dialog**.
   - View the customer message, AI triage classification (Category, Priority, Sentiment), Agent Execution Metrics (model, duration ms, tokens, estimated cost), and the complete state transition audit log.

---

## API Testing via cURL

### Ingest a Ticket via API Key (Idempotent):
```bash
curl -X POST http://localhost:8080/api/v1/tickets \
  -H "Content-Type: application/json" \
  -H "X-API-Key: sp_live_acme_demo_key_12345" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "customerEmail": "sarah.smith@example.com",
    "customerName": "Sarah Smith",
    "subject": "Order #ORD-1042 ANC Headphones not delivered",
    "body": "Hi, I placed order #ORD-1042 five days ago with express shipping. Where is my package?"
  }'
```

### Stream Live Events (SSE):
```bash
curl -N http://localhost:8080/api/v1/stream
```
