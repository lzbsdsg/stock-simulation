import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const STOCK_CODE = __ENV.STOCK_CODE || 'sh600519';
const ORDER_PRICE = Number(__ENV.ORDER_PRICE || '1688.88');
const ORDER_QUANTITY = Number(__ENV.ORDER_QUANTITY || '100');
const CANCEL_RATIO = Number(__ENV.CANCEL_RATIO || '0.25');
const VUS = Number(__ENV.VUS || '1000');
const DURATION = __ENV.DURATION || '10m';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'true').toLowerCase() === 'true';

export const options = {
  vus: VUS,
  duration: DURATION,
  thresholds: {
    http_req_failed: ['rate<0.005'],
    http_req_duration: ['p(99)<300'],
  },
};

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' };
  if (TOKEN) {
    headers.Authorization = `Bearer ${TOKEN}`;
  }
  if (K6_BYPASS_KEY) {
    headers['X-K6-Bypass-Key'] = K6_BYPASS_KEY;
  }
  return headers;
}

function randomClientOrderId() {
  return `k6-mix-${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random() * 10000)}`;
}

function hitMarket() {
  const quoteRes = http.get(`${BASE_URL}/api/v1/market/quote/${STOCK_CODE}`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-quote' },
  });
  check(quoteRes, {
    'mixed quote status expected': (r) => r.status === 200,
  });

  const quotesRes = http.get(`${BASE_URL}/api/v1/market/quotes?codes=${STOCK_CODE}&codes=sz000001`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-quotes' },
  });
  check(quotesRes, {
    'mixed quotes status expected': (r) => r.status === 200,
  });

  const searchRes = http.get(`${BASE_URL}/api/v1/market/search?keyword=%E8%8C%85%E5%8F%B0`, {
    headers: authHeaders(),
    tags: { endpoint: 'market-search' },
  });
  check(searchRes, {
    'mixed search status expected': (r) => r.status === 200,
  });
}

function hitPortfolio() {
  const overviewRes = http.get(`${BASE_URL}/api/v1/portfolio/overview`, {
    headers: authHeaders(),
    tags: { endpoint: 'portfolio-overview' },
  });
  check(overviewRes, {
    'mixed overview status expected': (r) => r.status === 200,
  });

  const positionsRes = http.get(`${BASE_URL}/api/v1/portfolio/positions`, {
    headers: authHeaders(),
    tags: { endpoint: 'portfolio-positions' },
  });
  check(positionsRes, {
    'mixed positions status expected': (r) => r.status === 200,
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
    'mixed list orders status expected': (r) => r.status === 200,
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
  if (roll < 0.60) {
    hitMarket();
  } else if (roll < 0.85) {
    hitPortfolio();
  } else {
    hitTrade();
  }

  sleep(0.3);
}