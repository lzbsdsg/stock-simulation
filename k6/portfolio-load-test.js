import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const DAYS = Number(__ENV.DAYS || '30');
const VUS = Number(__ENV.VUS || '100');
const DURATION = __ENV.DURATION || '5m';

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:overview}': ['p(99)<200'],
    'http_req_duration{endpoint:positions}': ['p(99)<200'],
    'http_req_duration{endpoint:fund-flows}': ['p(99)<300'],
    'http_req_duration{endpoint:equity-curve}': ['p(99)<300'],
  },
};

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (TOKEN) {
    headers.Authorization = `Bearer ${TOKEN}`;
  }
  return headers;
}

export function setup() {
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '3s' });
  if (health.status !== 200) {
    fail(`service not ready: GET /actuator/health -> ${health.status}`);
  }
}

export default function () {
  const headers = authHeaders();

  const overviewRes = http.get(`${BASE_URL}/api/v1/portfolio/overview`, {
    headers,
    tags: { endpoint: 'overview' },
  });
  check(overviewRes, {
    'overview status 200': (r) => r.status === 200,
  });

  const positionsRes = http.get(`${BASE_URL}/api/v1/portfolio/positions`, {
    headers,
    tags: { endpoint: 'positions' },
  });
  check(positionsRes, {
    'positions status 200': (r) => r.status === 200,
  });

  const flowsRes = http.get(`${BASE_URL}/api/v1/portfolio/fund-flows?page=1&size=20`, {
    headers,
    tags: { endpoint: 'fund-flows' },
  });
  check(flowsRes, {
    'fund flows status 200': (r) => r.status === 200,
  });

  const curveRes = http.get(`${BASE_URL}/api/v1/portfolio/equity-curve?days=${DAYS}`, {
    headers,
    tags: { endpoint: 'equity-curve' },
  });
  check(curveRes, {
    'equity curve status 200': (r) => r.status === 200,
  });

  sleep(1);
}

