# Technical Limitations, Trade-offs & Production Considerations

This document transparently outlines the architectural trade-offs, platform limitations, and production scaling considerations in SupportPilot.

---

## 1. Free-Tier Cloud Topology Trade-offs ($0 Constraints)

To satisfy the strict requirement that SupportPilot deploy and run permanently at **$0 monthly operating cost**, the system embraces several deliberate trade-offs:

| Component | Free Tier Constraint | Real-World Impact | Production Mitigation |
| :--- | :--- | :--- | :--- |
| **Render Web Service** | Spins down after 15 minutes of inactivity; 512MB RAM cap. | First incoming HTTP request after idle experiences a 40–50s cold start. JVM heap must stay below 350MB. | Use a ping monitor (e.g. UptimeRobot) or upgrade to Render Starter ($7/mo) with 1GB RAM and zero sleep. Use `-XX:+UseSerialGC` to prevent OOM killer. |
| **Neon PostgreSQL** | Inactivity auto-suspension; 0.5 CU compute. | Brief 2–3 second delay on initial connection handshake after idle periods. | Enable connection pooling via PgBouncer or migrate to dedicated RDS instance under sustained load. |
| **Google Gemini 1.5 Flash** | 15 Requests Per Minute (RPM); 1M tokens/min free quota. | High concurrency spikes above 15 tickets/minute will trigger HTTP 429 rate limits from Google AI Studio. | SupportPilot implements token-bucket rate limiting and exponential backoff retry. For enterprise scale, switch to Vertex AI pay-as-you-go or self-hosted vLLM. |

---

## 2. Architectural Trade-offs

### A. PostgreSQL SKIP LOCKED vs. Apache Kafka
- **Chosen Default:** PostgreSQL transactional queue using `SELECT ... FOR UPDATE SKIP LOCKED`.
- **Rationale:** Requires zero additional infrastructure, provides ACID guarantees alongside ticket state updates, and incurs $0 hosting cost.
- **Limitation:** Throughput caps around 5,000–10,000 writes/second due to WAL contention and row locking overhead.
- **Mitigation:** SupportPilot incorporates a pluggable hexagonal queue abstraction (`TicketQueue` port). Setting `supportpilot.queue.type=kafka` switches seamlessly to the high-throughput `KafkaTicketQueue` with tenant-keyed partitioning.

### B. In-Memory Token Bucket vs. Distributed Redis
- **Chosen Default:** High-performance concurrent in-memory `TenantRateLimiter` using `ConcurrentHashMap`.
- **Rationale:** Zero external Redis dependency, zero network hop latency (< 0.1ms token check).
- **Limitation:** Rate limit quotas are local to each JVM process. In a horizontally scaled multi-instance cluster behind a round-robin load balancer, effective tenant quotas would be multiplied by instance count.
- **Mitigation:** The `RateLimitFilter` interacts with a clean interface that can be backed by a Redis Lua script in multi-node deployments.

### C. Synchronous vs. Asynchronous Evaluation Runs
- **Current Approach:** The offline evaluation harness runs sequentially in JUnit or via Node script (`eval/runner.mjs`) to maintain deterministic ordering and exact per-ticket latency metrics.
- **Limitation:** Running 1,000+ test cases sequentially takes several minutes.
- **Mitigation:** For large golden datasets (> 500 cases), evaluation runs can be parallelized with a bounded virtual thread pool.

---

## 3. Vector Embeddings & RAG Grounding

- **Embedding Dimension:** Configured to standard 1536-dimensional vectors compatible with OpenAI text-embedding-3-small and Gemini text-embedding-004.
- **HNSW Indexing:** Enabled on PostgreSQL via pgvector extension (`vector_cosine_ops`).
- **Citation Precision:** While factual grounding reached 100% on policy compliance, short general inquiries (e.g. single-sentence questions) occasionally match multiple overlapping KB articles with borderline cosine similarity. Hybrid full-text lexical ranking (`tsvector`) was introduced to mitigate false positives.

---

## 4. Honest Unimplemented Scenarios

- **Real Payment Gateway Execution:** In this demo repository, `proposeRefund` records a structured financial intent requiring human approval, but does not invoke live Stripe or PayPal webhooks.
- **1-Million Ticket Load Run:** While `k6-load.js` and `k6-spike.js` test 100 concurrent VUs and burst conditions, running a continuous 1,000,000 ticket stress test against Neon free tier would exhaust Neon's free storage quota and Gemini's 15 RPM limit.
