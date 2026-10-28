# SupportPilot $0 Free-Tier Deployment Guide

Complete step-by-step instructions for deploying SupportPilot to production using 100% free tiers:
- **Database:** Neon Serverless Postgres (with `pgvector` enabled)
- **Backend API:** Render Free Web Service (Docker runtime)
- **Console Frontend:** Vercel Hobby Tier (Next.js serverless / edge)
- **LLM Engine:** Google AI Studio Gemini Free Tier

---

## 1. Database: Neon (Free Serverless Postgres + pgvector)

1. Sign up for free at [neon.tech](https://neon.tech).
2. Create a new project: `supportpilot`.
3. In the Neon SQL Editor, verify `pgvector` extension availability:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```
4. Copy the connection details from the Neon Dashboard:
   - **Host:** `ep-xyz.us-east-2.aws.neon.tech`
   - **Database:** `neondb` (or `supportpilot`)
   - **User:** `neondb_owner`
   - **Password:** `<neon_password>`
   - **Port:** `5432`

---

## 2. LLM Engine: Google AI Studio

1. Navigate to [aistudio.google.com](https://aistudio.google.com/).
2. Click **Get API key** and create a free tier key.
3. Keep this key handy for the Render environment variables below (`GEMINI_API_KEY`).

---

## 3. Backend API: Render (Free Web Service)

1. Sign up at [render.com](https://render.com).
2. Click **New +** $\to$ **Web Service**.
3. Connect your GitHub repository: `https://github.com/yashtiwari22/supportpilot`.
4. Configure service parameters:
   - **Name:** `supportpilot-api`
   - **Region:** Ohio (US East) or Frankfurt (EU Central)
   - **Language / Runtime:** `Docker`
   - **Docker Context Path:** `./backend`
   - **Dockerfile Path:** `./backend/Dockerfile`
   - **Instance Type:** `Free` ($0/month)
5. Under **Environment Variables**, set:
   | Key | Value | Notes |
   | :--- | :--- | :--- |
   | `PORT` | `8080` | Default container port |
   | `DB_HOST` | `<neon_host>` | e.g. `ep-xyz.us-east-2.aws.neon.tech` |
   | `DB_PORT` | `5432` | Standard Postgres port |
   | `DB_NAME` | `neondb` | Neon database name |
   | `DB_USER` | `neondb_owner` | Neon database user |
   | `DB_PASSWORD` | `<neon_password>` | Neon database password |
   | `LLM_PROVIDER` | `gemini` | Switch from `mock` to `gemini` |
   | `GEMINI_API_KEY` | `<ai_studio_key>` | Free tier key |
   | `GEMINI_MODEL` | `gemini-1.5-flash` | Free tier model |
   | `JWT_SECRET` | `generate-a-random-32-byte-string-here-1234567890` | Secret key for staff tokens |
   | `QUEUE_TYPE` | `postgres` | Default zero-cost queue |
6. Click **Create Web Service**.
7. Flyway will automatically run all migrations (`V1`, `V2`, `V3`) and seed the initial tenant and users on startup.
8. Note your public URL: `https://supportpilot-api.onrender.com`.

---

## 4. Frontend Console: Vercel (Hobby Tier)

1. Sign up at [vercel.com](https://vercel.com).
2. Click **Add New...** $\to$ **Project** $\to$ import your `supportpilot` GitHub repository.
3. Configure the project:
   - **Root Directory:** Edit and select `console`
   - **Framework Preset:** `Next.js`
4. Under **Environment Variables**, add:
   | Key | Value |
   | :--- | :--- |
   | `NEXT_PUBLIC_API_URL` | `https://supportpilot-api.onrender.com` |
5. Click **Deploy**.
6. Once deployed, open your Vercel URL (e.g. `https://supportpilot-console.vercel.app`).
7. Sign in with the seeded demo credentials:
   - **Email:** `admin@acme.com`
   - **Password:** `password123`

---

## 5. Health Check & Verification

Test your live deployment endpoints:

```bash
# 1. Health check
curl -s https://supportpilot-api.onrender.com/actuator/health

# 2. Prometheus metrics
curl -s https://supportpilot-api.onrender.com/actuator/prometheus | grep supportpilot

# 3. Test Ingestion with Demo API Key
curl -i -X POST https://supportpilot-api.onrender.com/api/v1/tickets \
  -H "X-API-Key: acme_live_test_api_key_12345" \
  -H "Content-Type: application/json" \
  -d '{
    "customerEmail": "customer@example.com",
    "customerName": "Test Customer",
    "subject": "Testing Live Deployment",
    "body": "Checking live production deployment on Render and Neon."
  }'
```
