// k6 行情接口压测脚本（迭代10联调版）
// 示例：
// k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e VUS=100 -e DURATION=5m k6/market-load-test.js
// 无 token 压测（需后端开启 app.security.k6-bypass）：
// k6 run -e BASE_URL=http://localhost:8080 -e K6_BYPASS_KEY=<bypassKey> k6/market-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const K6_BYPASS_KEY = __ENV.K6_BYPASS_KEY || '';
const VUS = Number(__ENV.VUS || '100');
const DURATION = __ENV.DURATION || '5m';
const TARGET_CODE = __ENV.TARGET_CODE || 'sh600519';
const KLINE_PREWARM_ENABLED = (__ENV.KLINE_PREWARM_ENABLED || 'true').toLowerCase() === 'true';
const KLINE_PREWARM_REPEAT = Math.max(Number(__ENV.KLINE_PREWARM_REPEAT || '2'), 1);
const KLINE_PREWARM_CODES = (__ENV.KLINE_PREWARM_CODES || TARGET_CODE)
    .split(',')
    .map((code) => code.trim())
    .filter(Boolean);

const DEFAULT_HEADERS = {};
if (TOKEN) {
    DEFAULT_HEADERS.Authorization = `Bearer ${TOKEN}`;
}
if (K6_BYPASS_KEY) {
    DEFAULT_HEADERS['X-K6-Bypass-Key'] = K6_BYPASS_KEY;
}

const KLINE_FROM = __ENV.KLINE_FROM || '2025-01-01';
const KLINE_TO = __ENV.KLINE_TO || '2025-12-31';

export const options = {
    vus: VUS,
    duration: DURATION,
    thresholds: {
        http_req_failed: ['rate<0.01'],
        'http_req_duration{endpoint:quote}': ['p(99)<200'],
        'http_req_duration{endpoint:quotes}': ['p(99)<220'],
        'http_req_duration{endpoint:kline}': ['p(99)<300'],
        'http_req_duration{endpoint:search}': ['p(99)<220'],
    },
};

function hit(endpointTag, path) {
    const response = http.get(`${BASE_URL}${path}`, {
        headers: DEFAULT_HEADERS,
        tags: { endpoint: endpointTag },
    });

    check(response, {
        [`${endpointTag} status is 200`]: (r) => r.status === 200,
        [`${endpointTag} has result code 200`]: (r) => {
            try {
                const payload = JSON.parse(r.body);
                return payload && payload.code === 200;
            } catch (_) {
                return false;
            }
        },
    });

    const cacheStatus = response.headers['X-Cache-Status'] || response.headers['x-cache-status'];
    check(cacheStatus, {
        [`${endpointTag} cache header exists`]: (value) => Boolean(value),
    });
}

function assertBusinessSuccess(response, label) {
    check(response, {
        [`${label} status is 200`]: (r) => r.status === 200,
        [`${label} has result code 200`]: (r) => {
            try {
                const payload = JSON.parse(r.body);
                return payload && payload.code === 200;
            } catch (_) {
                return false;
            }
        },
    });
}

export function setup() {
    if (!KLINE_PREWARM_ENABLED || KLINE_PREWARM_CODES.length === 0) {
        return;
    }

    for (const code of KLINE_PREWARM_CODES) {
        for (let i = 0; i < KLINE_PREWARM_REPEAT; i += 1) {
            const response = http.get(
                `${BASE_URL}/api/v1/market/kline/${code}?period=DAILY&from=${encodeURIComponent(KLINE_FROM)}&to=${encodeURIComponent(KLINE_TO)}`,
                {
                    headers: DEFAULT_HEADERS,
                    tags: { endpoint: i === 0 ? 'kline-prewarm-seed' : 'kline-prewarm-verify', stage: 'warmup' },
                },
            );
            assertBusinessSuccess(response, `kline prewarm ${code} #${i + 1}`);
        }
    }
}

export default function () {
    hit('quote', `/api/v1/market/quote/${TARGET_CODE}`);
    hit('quotes', `/api/v1/market/quotes?codes=${encodeURIComponent(TARGET_CODE)}&codes=sz000001&codes=sh601318`);
    hit(
        'kline',
        `/api/v1/market/kline/${TARGET_CODE}?period=DAILY&from=${encodeURIComponent(KLINE_FROM)}&to=${encodeURIComponent(KLINE_TO)}`,
    );
    hit('search', '/api/v1/market/search?keyword=%E8%8C%85%E5%8F%B0');
    sleep(0.4);
}
