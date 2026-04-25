import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://nginx';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const ACCEPT_429 = (__ENV.ACCEPT_429 || 'true').toLowerCase() === 'true';
const DURATION = __ENV.DURATION || '3m';

const FULL_CHAIN_RPS = Number(__ENV.FULL_CHAIN_RPS || '500');
const FULL_CHAIN_PRE_ALLOCATED_VUS = Number(__ENV.FULL_CHAIN_PRE_ALLOCATED_VUS || Math.max(100, Math.ceil(FULL_CHAIN_RPS * 0.6)));
const FULL_CHAIN_MAX_VUS = Number(__ENV.FULL_CHAIN_MAX_VUS || Math.max(400, FULL_CHAIN_RPS * 3));
const MARKET_RATIO = Number(__ENV.MARKET_RATIO || '0.65');
const PORTFOLIO_RATIO = Number(__ENV.PORTFOLIO_RATIO || '0.20');
const TRADE_RATIO = Number(__ENV.TRADE_RATIO || '0.15');

const QUOTE_RPS = Number(__ENV.QUOTE_RPS || '220');
const PORTFOLIO_RPS = Number(__ENV.PORTFOLIO_RPS || '120');
const TRADE_LIST_RPS = Number(__ENV.TRADE_LIST_RPS || '120');

const RATE_LIMIT_IDENTITY_PREFIX = __ENV.RATE_LIMIT_IDENTITY_PREFIX || 'k6-perf';
const K6_USER_ID_BASE = Number(__ENV.K6_USER_ID_BASE || '1000');
const K6_USER_ID_SPAN = Math.max(Number(__ENV.K6_USER_ID_SPAN || '500'), 1);
const STOCK_CODE = __ENV.STOCK_CODE || 'sh600519';
const PORTFOLIO_PAGE_SIZE = Number(__ENV.PORTFOLIO_PAGE_SIZE || '20');
const SEARCH_KEYWORD = __ENV.SEARCH_KEYWORD || '茅台';

if (ACCEPT_429) {
  http.setResponseCallback(http.expectedStatuses(200, 429));
}

const fullChainSuccessTotal = new Counter('full_chain_success_total');
const fullChainFailureTotal = new Counter('full_chain_failure_total');
const fullChainSuccessRate = new Rate('full_chain_success_rate');
const businessSuccessTotal = new Counter('business_success_total');
const businessFailureTotal = new Counter('business_failure_total');
const businessSuccessRate = new Rate('business_success_rate');
const fullChainDuration = new Trend('full_chain_duration_ms');

export const options = {
  discardResponseBodies: true,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    full_chain_mix: {
      executor: 'constant-arrival-rate',
      exec: 'fullChainMix',
      rate: FULL_CHAIN_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: FULL_CHAIN_PRE_ALLOCATED_VUS,
      maxVUs: FULL_CHAIN_MAX_VUS,
      tags: {
        scenario_kind: 'full_chain',
        configured_rate: String(FULL_CHAIN_RPS),
        pre_allocated_vus: String(FULL_CHAIN_PRE_ALLOCATED_VUS),
        max_vus: String(FULL_CHAIN_MAX_VUS),
      },
    },
    endpoint_market_quote: {
      executor: 'constant-arrival-rate',
      exec: 'marketQuoteOnly',
      rate: QUOTE_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(QUOTE_RPS * 0.5)),
      maxVUs: Math.max(200, QUOTE_RPS * 3),
      tags: { scenario_kind: 'single_endpoint', configured_rate: String(QUOTE_RPS) },
    },
    endpoint_portfolio_overview: {
      executor: 'constant-arrival-rate',
      exec: 'portfolioOverviewOnly',
      rate: PORTFOLIO_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(PORTFOLIO_RPS * 0.5)),
      maxVUs: Math.max(200, PORTFOLIO_RPS * 3),
      tags: { scenario_kind: 'single_endpoint', configured_rate: String(PORTFOLIO_RPS) },
    },
    endpoint_trade_list: {
      executor: 'constant-arrival-rate',
      exec: 'tradeListOnly',
      rate: TRADE_LIST_RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(50, Math.ceil(TRADE_LIST_RPS * 0.5)),
      maxVUs: Math.max(200, TRADE_LIST_RPS * 3),
      tags: { scenario_kind: 'single_endpoint', configured_rate: String(TRADE_LIST_RPS) },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_reqs{scenario:full_chain_mix}': ['count>0'],
    'http_reqs{scenario:endpoint_market_quote}': ['count>0'],
    'http_reqs{scenario:endpoint_portfolio_overview}': ['count>0'],
    'http_reqs{scenario:endpoint_trade_list}': ['count>0'],
    'http_req_failed{scenario:full_chain_mix}': ['rate<0.01'],
    'http_req_failed{scenario:endpoint_market_quote}': ['rate<0.01'],
    'http_req_failed{scenario:endpoint_portfolio_overview}': ['rate<0.01'],
    'http_req_failed{scenario:endpoint_trade_list}': ['rate<0.01'],
    'http_req_duration{scenario:full_chain_mix}': ['p(95)<180', 'p(99)<450'],
    'http_req_duration{scenario:endpoint_market_quote}': ['p(95)<120', 'p(99)<250'],
    'http_req_duration{scenario:endpoint_portfolio_overview}': ['p(95)<120', 'p(99)<280'],
    'http_req_duration{scenario:endpoint_trade_list}': ['p(95)<150', 'p(99)<300'],
    full_chain_success_rate: ['rate>0.99'],
    business_success_rate: ['rate>0.99'],
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

  const ok = check(res, {
    [`${tag} status expected`]: (r) => isExpectedStatus(r.status),
  });

  businessSuccessRate.add(ok);
  if (ok) {
    businessSuccessTotal.add(1);
  } else {
    businessFailureTotal.add(1);
  }

  return { res, ok };
}

export function fullChainMix() {
  const started = Date.now();
  let okCount = 0;
  let failCount = 0;

  const totalRatio = MARKET_RATIO + PORTFOLIO_RATIO + TRADE_RATIO;
  const normalizedMarket = totalRatio > 0 ? MARKET_RATIO / totalRatio : 0.65;
  const normalizedPortfolio = totalRatio > 0 ? PORTFOLIO_RATIO / totalRatio : 0.20;
  const roll = Math.random();

  if (roll < normalizedMarket) {
    const quote = hit(`/api/v1/market/quote/${STOCK_CODE}`, 'market-quote');
    quote.ok ? okCount += 1 : failCount += 1;

    const search = hit(`/api/v1/market/search?keyword=${encodeURIComponent(SEARCH_KEYWORD)}`, 'market-search');
    search.ok ? okCount += 1 : failCount += 1;
  } else if (roll < normalizedMarket + normalizedPortfolio) {
    const overview = hit('/api/v1/portfolio/overview', 'portfolio-overview');
    overview.ok ? okCount += 1 : failCount += 1;

    const positions = hit(`/api/v1/portfolio/positions?page=1&size=${PORTFOLIO_PAGE_SIZE}`, 'portfolio-positions');
    positions.ok ? okCount += 1 : failCount += 1;
  } else {
    const tradeList = hit(`/api/v1/trade/orders?scope=today&page=1&size=${PORTFOLIO_PAGE_SIZE}`, 'trade-list-orders');
    tradeList.ok ? okCount += 1 : failCount += 1;

    const trades = hit(`/api/v1/trade/trades?page=1&size=${PORTFOLIO_PAGE_SIZE}`, 'trade-trades');
    trades.ok ? okCount += 1 : failCount += 1;
  }

  const iterationOk = failCount === 0;
  fullChainSuccessRate.add(iterationOk);
  if (iterationOk) {
    fullChainSuccessTotal.add(1);
  } else {
    fullChainFailureTotal.add(1);
  }
  fullChainDuration.add(Date.now() - started);
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

function readMetric(data, key, field) {
  const metric = data.metrics[key];
  if (!metric) {
    return null;
  }
  if (metric[field] !== undefined) {
    return metric[field];
  }
  if (metric.values && metric.values[field] !== undefined) {
    return metric.values[field];
  }
  return null;
}

function readThresholdMetric(data, key, field) {
  const metric = data.metrics[key];
  if (!metric) {
    return null;
  }
  if (metric[field] !== undefined) {
    return metric[field];
  }
  if (metric.thresholds) {
    const fallbackKey = Object.keys(metric.thresholds)[0];
    const threshold = fallbackKey ? metric.thresholds[fallbackKey] : null;
    if (threshold && threshold[field] !== undefined) {
      return threshold[field];
    }
  }
  return null;
}

function durationSeconds(data) {
  const requestCount = readMetric(data, 'http_reqs', 'count') || 0;
  const requestRate = readMetric(data, 'http_reqs', 'rate') || 0;
  if (requestCount > 0 && requestRate > 0) {
    return Math.max(requestCount / requestRate, 1);
  }
  const fallback = 1;
  const testRunDurationMs = readMetric(data, 'iteration_duration', 'max') || 0;
  const wallMs = readMetric(data, 'http_req_duration', 'max') || 0;
  const scenarioDuration = Math.max(testRunDurationMs, wallMs, fallback * 1000);
  return Math.max(scenarioDuration / 1000, fallback);
}

export function handleSummary(data) {
  const totalReqs = readMetric(data, 'http_reqs', 'count') || 0;
  const totalQps = readMetric(data, 'http_reqs', 'rate') || 0;
  const totalFailedRate = readMetric(data, 'http_req_failed', 'value') || 0;
  const p95 = readMetric(data, 'http_req_duration', 'p(95)') || 0;
  const p99 = readMetric(data, 'http_req_duration', 'p(99)') || 0;
  const fullChainSuccess = readMetric(data, 'full_chain_success_total', 'count') || 0;
  const fullChainFailure = readMetric(data, 'full_chain_failure_total', 'count') || 0;
  const businessSuccess = readMetric(data, 'business_success_total', 'count') || 0;
  const businessFailure = readMetric(data, 'business_failure_total', 'count') || 0;
  const fullChainP95 = readMetric(data, 'full_chain_duration_ms', 'p(95)') || 0;
  const fullChainP99 = readMetric(data, 'full_chain_duration_ms', 'p(99)') || 0;
  const seconds = durationSeconds(data);

  const lines = [];
  lines.push('# Full-chain and Endpoint Performance Summary');
  lines.push('');
  lines.push('## Overall');
  lines.push('');
  lines.push(`- total_requests: ${totalReqs}`);
  lines.push(`- total_qps: ${totalQps.toFixed(2)}`);
  lines.push(`- total_failed_rate: ${(totalFailedRate * 100).toFixed(2)}%`);
  lines.push(`- total_p95_ms: ${p95.toFixed(2)}`);
  lines.push(`- total_p99_ms: ${p99.toFixed(2)}`);
  lines.push(`- business_success_total: ${businessSuccess}`);
  lines.push(`- business_failure_total: ${businessFailure}`);
  lines.push(`- business_success_per_second: ${(businessSuccess / seconds).toFixed(2)}`);
  lines.push('');
  lines.push('## Full-chain mix');
  lines.push('');
  lines.push(`- configured_rps: ${FULL_CHAIN_RPS}`);
  lines.push(`- market_ratio: ${MARKET_RATIO}`);
  lines.push(`- portfolio_ratio: ${PORTFOLIO_RATIO}`);
  lines.push(`- trade_ratio: ${TRADE_RATIO}`);
  lines.push(`- pre_allocated_vus: ${FULL_CHAIN_PRE_ALLOCATED_VUS}`);
  lines.push(`- max_vus: ${FULL_CHAIN_MAX_VUS}`);
  lines.push(`- full_chain_success_total: ${fullChainSuccess}`);
  lines.push(`- full_chain_failure_total: ${fullChainFailure}`);
  lines.push(`- full_chain_success_per_second: ${(fullChainSuccess / seconds).toFixed(2)}`);
  lines.push(`- full_chain_p95_ms: ${fullChainP95.toFixed(2)}`);
  lines.push(`- full_chain_p99_ms: ${fullChainP99.toFixed(2)}`);
  lines.push('');
  lines.push('## Per-scenario QPS');
  lines.push('');
  lines.push('| scenario | configured_rate | qps | success_per_second | p95(ms) | p99(ms) | failed_rate |');
  lines.push('|---|---:|---:|---:|---:|---:|---:|');

  const scenarioConfiguredRates = {
    full_chain_mix: FULL_CHAIN_RPS,
    endpoint_market_quote: QUOTE_RPS,
    endpoint_portfolio_overview: PORTFOLIO_RPS,
    endpoint_trade_list: TRADE_LIST_RPS,
  };

  Object.keys(data.metrics)
    .filter((k) => k.startsWith('http_reqs{scenario:'))
    .sort()
    .forEach((k) => {
      const scenario = k.replace('http_reqs{scenario:', '').replace('}', '');
      const scenarioQps = readMetric(data, k, 'rate') || 0;
      const durationKey = `http_req_duration{scenario:${scenario}}`;
      const failedKey = `http_req_failed{scenario:${scenario}}`;
      const scenarioP95 = readMetric(data, durationKey, 'p(95)') || 0;
      const scenarioP99 = readMetric(data, durationKey, 'p(99)') || 0;
      const scenarioFailed = readMetric(data, failedKey, 'value') || 0;
      const scenarioSuccessPerSecond = scenarioQps * (1 - scenarioFailed);
      const configuredRate = scenarioConfiguredRates[scenario] || 0;
      lines.push(`| ${scenario} | ${configuredRate} | ${scenarioQps.toFixed(2)} | ${scenarioSuccessPerSecond.toFixed(2)} | ${scenarioP95.toFixed(2)} | ${scenarioP99.toFixed(2)} | ${(scenarioFailed * 100).toFixed(2)}% |`);
    });

  lines.push('');
  lines.push('QPS definition: completed requests / test duration (k6 http_reqs.rate).');
  lines.push('success_per_second definition: qps × (1 - failed_rate).');

  return {
    stdout: `${lines.join('\n')}\n`,
  };
}
