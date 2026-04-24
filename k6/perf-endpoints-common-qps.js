import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://nginx';
const DURATION = __ENV.DURATION || '60s';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'false').toLowerCase() === 'true';

const STOCK_CODE = __ENV.STOCK_CODE || 'sh600519';
const SEARCH_KEYWORD = __ENV.SEARCH_KEYWORD || '平安';
const KLINE_FROM = __ENV.KLINE_FROM || '2026-01-01';
const KLINE_TO = __ENV.KLINE_TO || '2026-04-01';

const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '500'), 1);
const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-endpoints';

const endpointDefs = [
  { key: 'market_quote', env: 'RPS_MARKET_QUOTE', path: () => `/api/v1/market/quote/${STOCK_CODE}`, defaultRate: 220 },
  { key: 'market_quotes_batch', env: 'RPS_MARKET_QUOTES_BATCH', path: () => '/api/v1/market/quotes?codes=sh600519&codes=sz000001&codes=sh601318', defaultRate: 80 },
  { key: 'market_kline', env: 'RPS_MARKET_KLINE', path: () => `/api/v1/market/kline/${STOCK_CODE}?period=DAILY&from=${KLINE_FROM}&to=${KLINE_TO}`, defaultRate: 40 },
  { key: 'market_search', env: 'RPS_MARKET_SEARCH', path: () => `/api/v1/market/search?keyword=${encodeURIComponent(SEARCH_KEYWORD)}`, defaultRate: 60 },
  { key: 'market_listed', env: 'RPS_MARKET_LISTED', path: () => '/api/v1/market/listed?page=1&size=40', defaultRate: 70 },
  { key: 'market_indexes', env: 'RPS_MARKET_INDEXES', path: () => '/api/v1/market/indexes', defaultRate: 60 },
  { key: 'market_realtime_metrics', env: 'RPS_MARKET_REALTIME_METRICS', path: () => '/api/v1/market/realtime-metrics', defaultRate: 30 },
  { key: 'portfolio_overview', env: 'RPS_PORTFOLIO_OVERVIEW', path: () => '/api/v1/portfolio/overview', defaultRate: 120 },
  { key: 'portfolio_positions', env: 'RPS_PORTFOLIO_POSITIONS', path: () => '/api/v1/portfolio/positions?page=1&size=20', defaultRate: 90 },
  { key: 'portfolio_fund_flows', env: 'RPS_PORTFOLIO_FUND_FLOWS', path: () => '/api/v1/portfolio/fund-flows?page=1&size=20', defaultRate: 70 },
  { key: 'portfolio_equity_curve', env: 'RPS_PORTFOLIO_EQUITY_CURVE', path: () => '/api/v1/portfolio/equity-curve?days=30', defaultRate: 60 },
  { key: 'trade_orders', env: 'RPS_TRADE_ORDERS', path: () => '/api/v1/trade/orders?scope=today&page=1&size=20', defaultRate: 100 },
  { key: 'trade_trades', env: 'RPS_TRADE_TRADES', path: () => '/api/v1/trade/trades?page=1&size=20', defaultRate: 80 },
  { key: 'watchlist_get', env: 'RPS_WATCHLIST_GET', path: () => '/api/v1/watchlist', defaultRate: 80 },
  { key: 'notifications_get', env: 'RPS_NOTIFICATIONS_GET', path: () => '/api/v1/notifications?page=1&size=20', defaultRate: 70 },
  { key: 'notifications_unread_count', env: 'RPS_NOTIFICATIONS_UNREAD_COUNT', path: () => '/api/v1/notifications/unread-count', defaultRate: 60 },
  { key: 'user_me', env: 'RPS_USER_ME', path: () => '/api/v1/user/me', defaultRate: 60 },
];

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

function endpointRate(def) {
  const raw = __ENV[def.env];
  if (raw === undefined || raw === null || raw === '') {
    return def.defaultRate;
  }
  const value = Number(raw);
  return Number.isFinite(value) && value >= 0 ? value : def.defaultRate;
}

function expectedStatus(status) {
  if (status === 200) {
    return true;
  }
  return ACCEPT_429 && status === 429;
}

function buildOptions() {
  const scenarios = {};
  const thresholds = {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<500', 'p(99)<1200'],
    checks: ['rate>0.95'],
  };

  endpointDefs.forEach((def) => {
    const rate = endpointRate(def);
    if (rate <= 0) {
      return;
    }

    const scenarioName = `ep_${def.key}`;
    scenarios[scenarioName] = {
      executor: 'constant-arrival-rate',
      exec: def.key,
      rate,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(20, Math.ceil(rate * 0.5)),
      maxVUs: Math.max(100, rate * 3),
      tags: { endpoint_case: def.key },
    };

    thresholds[`http_reqs{scenario:${scenarioName}}`] = ['count>0'];
    thresholds[`http_req_duration{scenario:${scenarioName}}`] = ['p(95)<500', 'p(99)<1200'];
    thresholds[`http_req_failed{scenario:${scenarioName}}`] = ['rate<0.05'];
  });

  return {
    discardResponseBodies: true,
    scenarios,
    thresholds,
  };
}

export const options = buildOptions();

function hit(path, endpointTag) {
  const res = http.get(`${BASE_URL}${path}`, {
    headers: authHeaders(),
    tags: { endpoint: endpointTag },
  });

  check(res, {
    [`${endpointTag} status ok`]: (r) => expectedStatus(r.status),
  });

  return res;
}

export function market_quote() {
  hit(`/api/v1/market/quote/${STOCK_CODE}`, 'market_quote');
}

export function market_quotes_batch() {
  hit('/api/v1/market/quotes?codes=sh600519&codes=sz000001&codes=sh601318', 'market_quotes_batch');
}

export function market_kline() {
  hit(`/api/v1/market/kline/${STOCK_CODE}?period=DAILY&from=${KLINE_FROM}&to=${KLINE_TO}`, 'market_kline');
}

export function market_search() {
  hit(`/api/v1/market/search?keyword=${encodeURIComponent(SEARCH_KEYWORD)}`, 'market_search');
}

export function market_listed() {
  hit('/api/v1/market/listed?page=1&size=40', 'market_listed');
}

export function market_indexes() {
  hit('/api/v1/market/indexes', 'market_indexes');
}

export function market_realtime_metrics() {
  hit('/api/v1/market/realtime-metrics', 'market_realtime_metrics');
}

export function portfolio_overview() {
  hit('/api/v1/portfolio/overview', 'portfolio_overview');
}

export function portfolio_positions() {
  hit('/api/v1/portfolio/positions?page=1&size=20', 'portfolio_positions');
}

export function portfolio_fund_flows() {
  hit('/api/v1/portfolio/fund-flows?page=1&size=20', 'portfolio_fund_flows');
}

export function portfolio_equity_curve() {
  hit('/api/v1/portfolio/equity-curve?days=30', 'portfolio_equity_curve');
}

export function trade_orders() {
  hit('/api/v1/trade/orders?scope=today&page=1&size=20', 'trade_orders');
}

export function trade_trades() {
  hit('/api/v1/trade/trades?page=1&size=20', 'trade_trades');
}

export function watchlist_get() {
  hit('/api/v1/watchlist', 'watchlist_get');
}

export function notifications_get() {
  hit('/api/v1/notifications?page=1&size=20', 'notifications_get');
}

export function notifications_unread_count() {
  hit('/api/v1/notifications/unread-count', 'notifications_unread_count');
}

export function user_me() {
  hit('/api/v1/user/me', 'user_me');
}

function readMetric(data, key, field) {
  return data.metrics[key] && data.metrics[key][field] !== undefined ? data.metrics[key][field] : null;
}

export function handleSummary(data) {
  const totalReqs = readMetric(data, 'http_reqs', 'count') || 0;
  const totalQps = readMetric(data, 'http_reqs', 'rate') || 0;
  const totalFailedRate = readMetric(data, 'http_req_failed', 'value') || 0;
  const p95 = readMetric(data, 'http_req_duration', 'p(95)') || 0;
  const p99 = readMetric(data, 'http_req_duration', 'p(99)') || 0;

  const lines = [];
  lines.push('# Endpoint Performance Summary (Common QPS Method)');
  lines.push('');
  lines.push(`- total_requests: ${totalReqs}`);
  lines.push(`- total_qps: ${totalQps.toFixed(2)}`);
  lines.push(`- total_failed_rate: ${(totalFailedRate * 100).toFixed(2)}%`);
  lines.push(`- total_p95_ms: ${p95.toFixed(2)}`);
  lines.push(`- total_p99_ms: ${p99.toFixed(2)}`);
  lines.push('');
  lines.push('## Per-Scenario QPS');
  lines.push('');
  lines.push('| scenario | qps(http_reqs.rate) | p95(ms) | failed_rate |');
  lines.push('|---|---:|---:|---:|');

  Object.keys(data.metrics)
      .filter((k) => k.startsWith('http_reqs{scenario:ep_'))
      .sort()
      .forEach((k) => {
        const scenario = k.replace('http_reqs{scenario:', '').replace('}', '');
        const scenarioQps = readMetric(data, k, 'rate') || 0;
        const durationKey = `http_req_duration{scenario:${scenario}}`;
        const failedKey = `http_req_failed{scenario:${scenario}}`;
        const scenarioP95 = readMetric(data, durationKey, 'p(95)') || 0;
        const scenarioFailed = readMetric(data, failedKey, 'rate') || 0;
        lines.push(`| ${scenario} | ${scenarioQps.toFixed(2)} | ${scenarioP95.toFixed(2)} | ${(scenarioFailed * 100).toFixed(2)}% |`);
      });

  lines.push('');
  lines.push('QPS definition: completed requests / test duration (k6 http_reqs.rate).');

  return {
    stdout: `${lines.join('\n')}\n`,
  };
}
