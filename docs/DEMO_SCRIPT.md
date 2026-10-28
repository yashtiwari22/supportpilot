# SupportPilot: 3-Minute Walkthrough Demo Script

This script provides an exact, minute-by-minute walkthrough guide for reviewers, interviewers, or recorded video demonstrations of the SupportPilot platform.

---

## Pre-Demo Checklist
1. Docker Compose running: `docker compose -f infra/docker-compose.yml up -d`
2. Backend started: `cd backend && ./mvnw spring-boot:run` (running on `http://localhost:8080`)
3. Frontend Console started: `cd console && npm run dev` (running on `http://localhost:3000`)

---

## ⏱️ Minute 0:00 – 0:45: The Problem & Architecture

### Talking Points:
> *"SupportPilot is an event-driven, multi-tenant customer support platform designed to automate high-volume support inquiries without hallucination risks or accidental unauthorized financial actions."*
>
> *"It uses Hexagonal Architecture built with Java 21, Spring Boot, PostgreSQL with pgvector, and a Next.js 16 console with real-time Server-Sent Events. Rather than using an unconstrained single LLM call, it orchestrates four specialized agents running concurrently on Java Virtual Threads: Triage, Resolution with Hybrid RAG, Action with deterministic tools, and a CriticAgent reflection guardrail."*

### Actions:
1. Open browser to `http://localhost:3000`.
2. Point out the dark-mode aesthetic and the demo credentials quick-fill buttons.
3. Click **"Acme Electronics Admin"** (`admin@acme.com` / `password123`) and click **Sign In**.

---

## ⏱️ Minute 0:45 – 1:30: Real-Time Multi-Agent Ingestion & SSE

### Talking Points:
> *"Once logged in, notice the real-time Server-Sent Events connection indicator in the header. We can inject live test traffic using our built-in simulator."*
>
> *"When tickets enter the platform, the Token-Bucket rate limiter validates tenant quotas, writes the ticket to PostgreSQL, and enqueues a background job via SKIP LOCKED concurrency or Apache Kafka. A pool of virtual thread workers picks up jobs, progresses the state machine, and streams updates back to the UI."*

### Actions:
1. Click the purple **"Simulate Tickets"** button in the header.
2. Select **3 Tickets** and click **Run Simulator**.
3. Point to the tickets appearing in **Ingested** and transitioning to **AI Triaged** and **AI Drafted** in real time without refreshing the page.
4. Click on any ticket to open the **Ticket Detail Dialog**:
   - Highlight the **Triage Classification** (Category, Priority, Sentiment).
   - Scroll down to the **Audit & Agent Timeline** showing exact latency (ms), token consumption, and model costs for each agent step.

---

## ⏱️ Minute 1:30 – 2:15: Hybrid RAG, Tools & Human-in-the-Loop Gate

### Talking Points:
> *"Notice what happened with customer Sarah Connor's ticket regarding a damaged ANC Headphone. The ResolutionAgent executed a hybrid search combining pgvector cosine similarity with full-text keyword ranking across our knowledge base."*
>
> *"Next, the ActionAgent checked order ORD-1042, verified return eligibility, and proposed a $149.99 refund. But rather than executing a financial payout automatically, our CriticAgent intercepted the draft, flagged the refund action as high risk, and safely gated the ticket into the Human Approval Queue."*

### Actions:
1. Click the **"Approval Queue"** tab in the top navigation bar.
2. Observe the pending approval card for Sarah Connor with the AI draft, proposed refund action, and critic critique notes.
3. Demonstrate human supervisory control by clicking **"Approve"**.
4. Switch back to the **Live Board** to see the ticket instantly move to **AI Resolved / Sent**.

---

## ⏱️ Minute 2:15 – 3:00: Reliability, Metrics & Evaluation Harness

### Talking Points:
> *"SupportPilot is built with strict enterprise reliability controls:"*
>
> 1. *"**Dead-Letter Queue (DLQ):** Poison-pill messages that exhaust retry limits are routed to the DLQ with exponential backoff and manual redrive APIs."*
> 2. *"**System & AI Metrics:** The Metrics tab provides live telemetry on queue depth, per-agent latency, and token cost."*
> 3. *"**Golden Dataset Evaluation:** Finally, the Evaluation tab displays real benchmarks run against our golden dataset, proving 100% triage accuracy, 100% tool precision, and zero unauthorized prompt injection executions."*

### Actions:
1. Click the **"DLQ"** tab to highlight the poison-pill monitoring station and single/bulk redrive capabilities.
2. Click the **"Metrics"** tab to show live per-agent latency and token cost breakdown.
3. Click the **"Evaluation"** tab to review the golden dataset accuracy and safety guardrail verification.
4. Conclude:
   > *"Everything in SupportPilot runs completely on free cloud tiers—Neon PostgreSQL, Render, Vercel, and Gemini Flash—providing an enterprise-grade AI architecture at zero operating cost."*
