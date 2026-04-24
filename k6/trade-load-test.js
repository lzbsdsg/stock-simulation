import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const STOCK_CODE = __ENV.STOCK_CODE || 'sz000001';
const ORDER_PRICE = Number(__ENV.ORDER_PRICE || '10.50');
const ORDER_QUANTITY = Number(__ENV.ORDER_QUANTITY || '100');
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || '0.3');
const VUS = Number(__ENV.VUS || '200');
const DURATION = __ENV.DURATION || '5m';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'false').toLowerCase() === 'true';
const HEALTH_PATH = __ENV.HEALTH_PATH || '/actuator/health';
const HEALTH_TIMEOUT = __ENV.HEALTH_TIMEOUT || '15s';
const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-trade';
const AUTO_ORDER_PRICE = (__ENV.AUTO_ORDER_PRICE || 'true').toLowerCase() === 'true';
const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '200'), 1);

const hardFailureRate = new Rate('hard_failure_rate');

if (ACCEPT_429) {
  http.setResponseCallback(http.expectedStatuses(200, 429));
}

const thresholds = {
  http_req_duration: ['p(95)<1500', 'p(99)<2500'],
  'http_req_duration{endpoint:trade-place-order}': ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{endpoint:trade-list-orders}': ['p(95)<500', 'p(99)<1000'],
  'http_req_duration{endpoint:trade-cancel-order}': ['p(95)<500', 'p(99)<1000'],
  hard_failure_rate: ['rate<0.01'],
};
if (!ACCEPT_429) {
  thresholds.http_req_failed = ['rate<0.01'];
}

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds,
};

function effectiveUserId() {
  return K6_USER_ID_BASE + ((__VU - 1) % K6_USER_ID_SPAN);
}

function authHeaders() {
  const headers = {
    'Content-Type': 'application/json',
    'X-RateLimit-Identity': `${RATE_LIMIT_IDENTITY_PREFIX}-${__VU}`,
  };
  if (TOKEN) {
    headers['Authorization'] = `Bearer ${TOKEN}`;
  }
  if (K6_BYPASS_KEY) {
    headers['X-K6-Bypass-Key'] = K6_BYPASS_KEY;
    headers['X-K6-Bypass-User-Id'] = String(effectiveUserId());
  }
  return headers;
}

function buildClientOrderId() {
  return `k6-${effectiveUserId()}-${Date.now()}-${__VU}-${__ITER}`;
}

function isNetworkOrServerFailure(res) {
  return res.status === 0 || res.status >= 500;
}

export function setup() {
  const healthRes = http.get(`${BASE_URL}${HEALTH_PATH}`, { timeout: HEALTH_TIMEOUT });
  if (healthRes.status !== 200) {
    fail(`service not ready: GET ${HEALTH_PATH} -> ${healthRes.status}`);
  }

  if (!AUTO_ORDER_PRICE) {
    return { orderPrice: ORDER_PRICE };
  }

  const quoteRes = http.get(`${BASE_URL}/api/v1/market/quote/${STOCK_CODE}`, {
    headers: authHeaders(),
    tags: { endpoint: 'trade-price-quote' },
    timeout: HEALTH_TIMEOUT,
  });
  if (quoteRes.status !== 200) {
    fail(`price quote failed: GET /api/v1/market/quote/${STOCK_CODE} -> ${quoteRes.status}`);
  }
  try {
    const body = JSON.parse(quoteRes.body);
    const quotedPrice = Number(body?.data?.currentPrice);
    if (!Number.isFinite(quotedPrice) || quotedPrice <= 0) {
      fail(`invalid quote price for ${STOCK_CODE}`);
    }
    return { orderPrice: quotedPrice };
  } catch (e) {
    fail(`failed to parse quote response for ${STOCK_CODE}: ${e}`);
  }
}

export default function (data) {
  const effectiveOrderPrice =
    data && Number.isFinite(Number(data.orderPrice)) ? Number(data.orderPrice) : ORDER_PRICE;
  const payload = JSON.stringify({
    clientOrderId: buildClientOrderId(),
    stockCode: STOCK_CODE,
    side: 'BUY',
    orderType: 'LIMIT',
    price: effectiveOrderPrice,
    quantity: ORDER_QUANTITY,
  });

  const placeRes = http.post(`${BASE_URL}/api/v1/trade/orders`, payload, {
    headers: authHeaders(),
    tags: { endpoint: 'trade-place-order' },
  });
  hardFailureRate.add(isNetworkOrServerFailure(placeRes));

  const placed = check(placeRes, {
    'place order status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
    'place order has data': (r) => {
      if (r.status !== 200) {
        return ACCEPT_429 && r.status === 429;
      }
      try {
        const body = JSON.parse(r.body);
        return !!body?.data?.orderId;
      } catch (e) {
        return false;
      }
    },
  });

  let orderId = null;
  if (placed && placeRes.status === 200) {
    try {
      const body = JSON.parse(placeRes.body);
      orderId = body?.data?.orderId || null;
    } catch (e) {
      orderId = null;
    }
  }

  const listRes = http.get(`${BASE_URL}/api/v1/trade/orders?scope=today&page=1&size=20`, {
    headers: authHeaders(),
    tags: { endpoint: 'trade-list-orders' },
  });
  hardFailureRate.add(isNetworkOrServerFailure(listRes));
  check(listRes, {
    'list orders status 200': (r) => r.status === 200,
  });

  if (orderId && Math.random() < CANCEL_RATIO) {
    const cancelRes = http.del(`${BASE_URL}/api/v1/trade/orders/${orderId}`, null, {
      headers: authHeaders(),
      tags: { endpoint: 'trade-cancel-order' },
    });
    hardFailureRate.add(isNetworkOrServerFailure(cancelRes));
    check(cancelRes, {
      'cancel status expected': (r) => (ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200),
    });
  }

  sleep(1);
}
