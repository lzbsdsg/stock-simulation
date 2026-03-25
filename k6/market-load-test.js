// k6 行情接口压测脚本（迭代10联调版）
// 示例：
// k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<accessToken> -e VUS=100 -e DURATION=5m k6/market-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const VUS = Number(__ENV.VUS || '100');
const DURATION = __ENV.DURATION || '5m';

const DEFAULT_HEADERS = TOKEN
    ? { Authorization: `Bearer ${TOKEN}` }
    : {};

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

export default function () {
    hit('quote', '/api/v1/market/quote/sh600519');
    hit('quotes', '/api/v1/market/quotes?codes=sh600519&codes=sz000001&codes=sh601318');
    hit(
        'kline',
        `/api/v1/market/kline/sh600519?period=DAILY&from=${encodeURIComponent(KLINE_FROM)}&to=${encodeURIComponent(KLINE_TO)}`,
    );
    hit('search', '/api/v1/market/search?keyword=%E8%8C%85%E5%8F%B0');
    sleep(0.4);
}
