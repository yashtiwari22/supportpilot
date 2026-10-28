import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metric tracking error rate
export const errorRate = new Rate('errors');

// 100 Virtual Users peak load scenario
export const options = {
  stages: [
    { duration: '10s', target: 20 },  // Ramp-up to 20 VUs
    { duration: '20s', target: 50 },  // Ramp-up to 50 VUs
    { duration: '20s', target: 100 }, // Peak 100 VUs
    { duration: '10s', target: 0 },   // Ramp-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1200'], // 95% of requests must complete below 500ms
    errors: ['rate<0.05'],                          // Error rate under 5%
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'sp_live_acme_demo_key_12345';

export default function () {
  const rand = Math.random();

  if (rand < 0.6) {
    // 60% of traffic: Ticket ingestion with unique Idempotency Key
    const payload = JSON.stringify({
      customerEmail: `loadtest_${__VU}_${__ITER}@example.com`,
      customerName: `VU ${__VU} User`,
      subject: `Order Inquiry ORD-${1000 + (__ITER % 500)}`,
      body: 'Where is my order? Automated load test message checking system throughput.',
    });

    const params = {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': API_KEY,
        'Idempotency-Key': `k6-idemp-${__VU}-${__ITER}-${Date.now()}`,
      },
    };

    const res = http.post(`${BASE_URL}/api/v1/tickets`, payload, params);
    const passed = check(res, {
      'ingestion status is 201 or 429': (r) => r.status === 201 || r.status === 429,
      'has idempotency key header or id': (r) => r.status === 201 ? r.json('id') !== undefined : true,
    });
    errorRate.add(!passed);
  } else if (rand < 0.85) {
    // 25% of traffic: Ticket list queries with filters
    const params = {
      headers: {
        'X-API-Key': API_KEY,
      },
    };

    const res = http.get(`${BASE_URL}/api/v1/tickets?status=RECEIVED&limit=20`, params);
    const passed = check(res, {
      'list status is 200': (r) => r.status === 200,
    });
    errorRate.add(!passed);
  } else {
    // 15% of traffic: Queue stats & health checks
    const params = {
      headers: {
        'X-API-Key': API_KEY,
      },
    };

    const res = http.get(`${BASE_URL}/api/v1/queue/stats`, params);
    const passed = check(res, {
      'queue stats status is 200': (r) => r.status === 200,
    });
    errorRate.add(!passed);
  }

  // Paced user think-time between 100ms and 300ms
  sleep(0.1 + Math.random() * 0.2);
}
