import http from 'k6/http';
import { check, sleep } from 'k6';

// Rapid traffic spike testing Token Bucket rate limiting
export const options = {
  stages: [
    { duration: '3s', target: 10 },
    { duration: '5s', target: 80 },  // Immediate spike
    { duration: '10s', target: 80 }, // Sustained pressure
    { duration: '3s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<400'],
  },
};

const BASE_URL = __ENV.API_URL || 'http://localhost:8080';
const API_KEY = __ENV.API_KEY || 'sp_live_acme_demo_key_12345';

export default function () {
  const payload = JSON.stringify({
    customerEmail: `spike_${__VU}@example.com`,
    customerName: `Spike User ${__VU}`,
    subject: 'Spike Test Ticket',
    body: 'Testing token-bucket rate limiter under sudden burst traffic.',
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': API_KEY,
      'Idempotency-Key': `spike-${__VU}-${__ITER}-${Date.now()}`,
    },
  };

  const res = http.post(`${BASE_URL}/api/v1/tickets`, payload, params);

  check(res, {
    'returns either 201 or 429': (r) => r.status === 201 || r.status === 429,
    'has rate limit limit header': (r) => r.headers['X-Ratelimit-Limit'] !== undefined || r.headers['x-ratelimit-limit'] !== undefined,
  });

  sleep(0.05);
}
