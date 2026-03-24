import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const STOCK_CODE = __ENV.STOCK_CODE || 'sh600519';
const ORDER_PRICE = Number(__ENV.ORDER_PRICE || '1688.88');
const ORDER_QUANTITY = Number(__ENV.ORDER_QUANTITY || '100');
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || '0.3');
const VUS = Number(__ENV.VUS || '200');
const DURATION = __ENV.DURATION || '5m';

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_duration: ['p(95)<100', 'p(99)<200'],
    http_req_failed: ['rate<0.001'],
  },
};

function authHeaders() {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (TOKEN) {
    headers['Authorization'] = `Bearer ${TOKEN}`;
  }
  return headers;
}

function buildClientOrderId() {
  return `k6-${Date.now()}-${__VU}-${__ITER}`;
}

export default function () {
  const payload = JSON.stringify({
    clientOrderId: buildClientOrderId(),
    stockCode: STOCK_CODE,
    side: 'BUY',
    orderType: 'LIMIT',
    price: ORDER_PRICE,
    quantity: ORDER_QUANTITY,
  });

  const placeRes = http.post(`${BASE_URL}/api/v1/trade/orders`, payload, {
    headers: authHeaders(),
  });

  const placed = check(placeRes, {
    'place order status 200': (r) => r.status === 200,
    'place order has data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return !!body?.data?.orderId;
      } catch (e) {
        return false;
      }
    },
  });

  let orderId = null;
  if (placed) {
    orderId = JSON.parse(placeRes.body).data.orderId;
  }

  const listRes = http.get(`${BASE_URL}/api/v1/trade/orders?scope=today&page=1&size=20`, {
    headers: authHeaders(),
  });
  check(listRes, {
    'list orders status 200': (r) => r.status === 200,
  });

  if (orderId && Math.random() < CANCEL_RATIO) {
    const cancelRes = http.del(`${BASE_URL}/api/v1/trade/orders/${orderId}`, null, {
      headers: authHeaders(),
    });
    check(cancelRes, {
      'cancel status 200': (r) => r.status === 200,
    });
  }

  sleep(1);
}
