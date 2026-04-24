import http from 'k6/http';
import { check, fail, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const DAYS = Number(__ENV.DAYS || '30');
const VUS = Number(__ENV.VUS || '100');
const DURATION = __ENV.DURATION || '5m';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'false').toLowerCase() === 'true';
const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-portfolio';
const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '200'), 1);

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    ...(ACCEPT_429 ? {} : { http_req_failed: ['rate<0.01'] }),
    'http_req_duration{endpoint:overview}': ['p(99)<200'],
    'http_req_duration{endpoint:positions}': ['p(99)<200'],
    'http_req_duration{endpoint:fund-flows}': ['p(99)<300'],
    'http_req_duration{endpoint:equity-curve}': ['p(99)<300'],
  },
};

if (ACCEPT_429) {
  http.setResponseCallback(http.expectedStatuses(200, 429));
}

function effectiveUserId() {
  return K6_USER_ID_BASE + ((__VU - 1) % K6_USER_ID_SPAN);
}

function authHeaders() {
  const headers = {
    'Content-Type': 'application/json',
    'X-RateLimit-Identity': `${RATE_LIMIT_IDENTITY_PREFIX}-${__VU}`,
  };
  if (TOKEN) {
    headers.Authorization = `Bearer ${TOKEN}`;
  }
  if (K6_BYPASS_KEY) {
    headers['X-K6-Bypass-Key'] = K6_BYPASS_KEY;
    headers['X-K6-Bypass-User-Id'] = String(effectiveUserId());
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
    'overview status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
  });

  const positionsRes = http.get(`${BASE_URL}/api/v1/portfolio/positions`, {
    headers,
    tags: { endpoint: 'positions' },
  });
  check(positionsRes, {
    'positions status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
  });

  const flowsRes = http.get(`${BASE_URL}/api/v1/portfolio/fund-flows?page=1&size=20`, {
    headers,
    tags: { endpoint: 'fund-flows' },
  });
  check(flowsRes, {
    'fund flows status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
  });

  const curveRes = http.get(`${BASE_URL}/api/v1/portfolio/equity-curve?days=${DAYS}`, {
    headers,
    tags: { endpoint: 'equity-curve' },
  });
  check(curveRes, {
    'equity curve status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
  });

  sleep(1);
}

