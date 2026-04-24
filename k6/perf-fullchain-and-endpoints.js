import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://nginx';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'true').toLowerCase() === 'true';

const FULL_CHAIN_RPS = Number(__ENV.FULL_CHAIN_RPS || '500');
const QUOTE_RPS = Number(__ENV.QUOTE_RPS || '220');
const PORTFOLIO_RPS = Number(__ENV.PORTFOLIO_RPS || '120');
const TRADE_LIST_RPS = Number(__ENV.TRADE_LIST_RPS || '120');
const DURATION = __ENV.DURATION || '3m';

const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-perf';
const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '500'), 1);

const STOCK_CODE = __ENV.STOCK_CODE || 'sh600519';
const PORTFOLIO_PAGE_SIZE = Number(__ENV.PORTFOLIO_PAGE_SIZE || '20');

if (ACCEPT_429) {
  http.setResponseCallback(http.expectedStatuses(200, 429));
}

export const options = {
  discardResponseBodies: true,
  scenarios: {
    full_chain_mix: {
      executor: 'constant-arrival-rate',
      exec: 'fullChainMix',
      rate: FULL_CHAIN_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(100, Math.ceil(FULL_CHAIN_RPS * 0.6)),
      maxVUs: Math.max(400, FULL_CHAIN_RPS * 3),
    },
    endpoint_market_quote: {
      executor: 'constant-arrival-rate',
      exec: 'marketQuoteOnly',
      rate: QUOTE_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(QUOTE_RPS * 0.5)),
      maxVUs: Math.max(200, QUOTE_RPS * 3),
    },
    endpoint_portfolio_overview: {
      executor: 'constant-arrival-rate',
      exec: 'portfolioOverviewOnly',
      rate: PORTFOLIO_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(PORTFOLIO_RPS * 0.5)),
      maxVUs: Math.max(200, PORTFOLIO_RPS * 3),
    },
    endpoint_trade_list: {
      executor: 'constant-arrival-rate',
      exec: 'tradeListOnly',
      rate: TRADE_LIST_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(TRADE_LIST_RPS * 0.5)),
      maxVUs: Math.max(200, TRADE_LIST_RPS * 3),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{scenario:full_chain_mix}': ['p(95)<150', 'p(99)<450'],
    'http_req_duration{scenario:endpoint_market_quote}': ['p(95)<120', 'p(99)<250'],
    'http_req_duration{scenario:endpoint_portfolio_overview}': ['p(95)<120', 'p(99)<280'],
    'http_req_duration{scenario:endpoint_trade_list}': ['p(95)<150', 'p(99)<300'],
    checks: ['rate>0.99'],
  },
};

function effectiveUserId() {
  return K6_USER_ID_BASE + ((__VU - 1) % K6_USER_ID_SPAN);
}

function authHeaders() {
  const headers = {
    'Content-Type': 'application/json',
    'X-RateLimit-Identity': `${RATE_LIMIT_IDENTITY_PREFIX}-${__VU}`,
  };
  if (K6_BYPASS_KEY) {
    headers['X-K6-Bypass-Key'] = K6_BYPASS_KEY;
    headers['X-K6-Bypass-User-Id'] = String(effectiveUserId());
  }
  return headers;
}

function isExpectedStatus(status) {
  return ACCEPT_429 ? status === 200 || status === 429 : status === 200;
}

function hit(path, tag) {
  const res = http.get(`${BASE_URL}${path}`, {
    headers: authHeaders(),
    tags: { endpoint: tag },
  });

  check(res, {
    [`${tag} status expected`]: (r) => isExpectedStatus(r.status),
  });

  return res;
}

export function fullChainMix() {
  hit(`/api/v1/market/quote/${STOCK_CODE}`, 'market-quote');

  if (__ITER % 2 === 0) {
    hit('/api/v1/portfolio/overview', 'portfolio-overview');
  }

  if (__ITER % 3 === 0) {
    hit(`/api/v1/trade/orders?scope=today&page=1&size=${PORTFOLIO_PAGE_SIZE}`, 'trade-list-orders');
  }

  sleep(0.02);
}

export function marketQuoteOnly() {
  hit(`/api/v1/market/quote/${STOCK_CODE}`, 'market-quote-only');
}

export function portfolioOverviewOnly() {
  hit('/api/v1/portfolio/overview', 'portfolio-overview-only');
}

export function tradeListOnly() {
  hit(`/api/v1/trade/orders?scope=today&page=1&size=${PORTFOLIO_PAGE_SIZE}`, 'trade-list-only');
}
