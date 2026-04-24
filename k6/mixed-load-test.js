import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const STOCK_CODE = __ENV.STOCK_CODE || 'sz000001';
const ORDER_PRICE = Number(__ENV.ORDER_PRICE || '10.50');
const ORDER_QUANTITY = Number(__ENV.ORDER_QUANTITY || '100');
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || '0.25');
const VUS = Number(__ENV.VUS || '1000');
const DURATION = __ENV.DURATION || '10m';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'true').toLowerCase() === 'true';
const MARKET_RATIO = Number(__ENV.MARKET_RATIO || '0.70');
const PORTFOLIO_RATIO = Number(__ENV.PORTFOLIO_RATIO || '0.10');
const REQUEST_SLEEP = Number(__ENV.REQUEST_SLEEP || '0.5');
const PORTFOLIO_PAGE_SIZE = Number(__ENV.PORTFOLIO_PAGE_SIZE || '20');
const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-mixed';
const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '200'), 1);

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.005'],
    http_req_duration: ['p(99)<300'],
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

function randomClientOrderId() {
  return `k6-mix-${effectiveUserId()}-${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random() * 10000)}`;
}

function hitMarket() {
  const quoteRes = http.get(`${BASE_URL}/api/v1/market/quote/${STOCK_CODE}`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-quote' },
  });
  check(quoteRes, {
    'mixed quote status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });

  const quotesRes = http.get(`${BASE_URL}/api/v1/market/quotes?codes=${STOCK_CODE}&codes=sz000001`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-quotes' },
  });
  check(quotesRes, {
    'mixed quotes status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });

  const searchRes = http.get(`${BASE_URL}/api/v1/market/search?keyword=%E8%8C%85%E5%8F%B0`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-search' },
  });
  check(searchRes, {
    'mixed search status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });
}

function hitPortfolio() {
  const overviewRes = http.get(`${BASE_URL}/api/v1/portfolio/overview`, {
    headers: authHeaders(),
    tags: { endpoint: 'portfolio-overview' },
  });
  check(overviewRes, {
    'mixed overview status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });

  const positionsRes = http.get(`${BASE_URL}/api/v1/portfolio/positions?page=1&size=${PORTFOLIO_PAGE_SIZE}`, {
    headers: authHeaders(),
    tags: { endpoint: 'portfolio-positions' },
  });
  check(positionsRes, {
    'mixed positions status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });
}

function hitTrade() {
  const payload = JSON.stringify({
    clientOrderId: randomClientOrderId(),
    stockCode: STOCK_CODE,
    side: 'BUY',
    orderType: 'LIMIT',
    price: ORDER_PRICE,
    quantity: ORDER_QUANTITY,
  });

  const placeRes = http.post(`${BASE_URL}/api/v1/trade/orders`, payload, {
    headers: authHeaders(),
    tags: { endpoint: 'trade-place-order' },
  });

  const placed = check(placeRes, {
    'mixed place order status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });

  let orderId = null;
  if (placed && placeRes.status === 200) {
    try {
      const body = JSON.parse(placeRes.body);
      orderId = body?.data?.orderId || null;
    } catch (_) {
      orderId = null;
    }
  }

  const listRes = http.get(`${BASE_URL}/api/v1/trade/orders?scope=today&page=1&size=20`, {
    headers: authHeaders(),
    tags: { endpoint: 'trade-list-orders' },
  });
  check(listRes, {
    'mixed list orders status expected': (r) =>
      ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
  });

  if (orderId && Math.random() < CANCEL_RATIO) {
    const cancelRes = http.del(`${BASE_URL}/api/v1/trade/orders/${orderId}`, null, {
      headers: authHeaders(),
      tags: { endpoint: 'trade-cancel-order' },
    });
    check(cancelRes, {
      'mixed cancel order status expected': (r) =>
        ACCEPT_429 ? r.status === 200 || r.status === 429 : r.status === 200,
    });
  }
}

export default function () {
  const roll = Math.random();
  const marketBoundary = Math.min(Math.max(MARKET_RATIO, 0), 1);
  const portfolioBoundary = Math.min(Math.max(marketBoundary + PORTFOLIO_RATIO, 0), 1);

  if (roll < marketBoundary) {
    hitMarket();
  } else if (roll < portfolioBoundary) {
    hitPortfolio();
  } else {
    hitTrade();
  }

  sleep(REQUEST_SLEEP);
}
