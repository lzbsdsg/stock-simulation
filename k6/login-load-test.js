import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.EMAIL || '';
const PASSWORD = __ENV.PASSWORD || '';
const VUS = Number(__ENV.VUS || '100');
const DURATION = __ENV.DURATION || '3m';

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.001'],
    'http_req_duration{endpoint:login}': ['p(99)<300'],
  },
};

function loginBody() {
  return JSON.stringify({
    email: EMAIL,
    password: PASSWORD,
  });
}

export default function () {
  const response = http.post(`${BASE_URL}/api/v1/auth/login`, loginBody(), {
    headers: { 'Content-Type': 'application/json' },
    tags: { endpoint: 'login' },
  });

  check(response, {
    'login status is 200': (r) => r.status === 200,
    'login has result code 200': (r) => {
      try {
        const payload = JSON.parse(r.body);
        return payload?.code === 200;
      } catch (_) {
        return false;
      }
    },
    'login has access token': (r) => {
      try {
        const payload = JSON.parse(r.body);
        return Boolean(payload?.data?.accessToken);
      } catch (_) {
        return false;
      }
    },
  });

  sleep(0.5);
}