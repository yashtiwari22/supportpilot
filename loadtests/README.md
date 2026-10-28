# SupportPilot Load Testing Suite

High-throughput stress and spike test scenarios implemented with [k6](https://k6.io/).

---

## 1. Prerequisites

Install `k6`:
- **macOS (Homebrew):**
  ```bash
  brew install k6
  ```
- **Linux (Debian/Ubuntu):**
  ```bash
  sudo gpg -k
  sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
  echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
  sudo apt-get update && sudo apt-get install k6
  ```

---

## 2. Running Load Tests

Ensure the backend server is running on `http://localhost:8080`.

### Standard Concurrency Scenario (100 VUs)
Ramps up to 100 concurrent virtual users exercising mixed read/write traffic:
```bash
k6 run loadtests/k6-load.js
```

### Rate Limiter Burst / Spike Scenario
Instantaneous burst from 0 to 80 users to verify token-bucket rate limiting (HTTP 429 and `X-RateLimit-*` headers):
```bash
k6 run loadtests/k6-spike.js
```

### Custom Target Environment
To test against a staging or deployed Render environment:
```bash
API_URL="https://supportpilot-api.onrender.com" API_KEY="acme_live_test_api_key_12345" k6 run loadtests/k6-load.js
```
