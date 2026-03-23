// k6 行情接口压测脚本
// 运行: k6 run --vus 500 --duration 5m k6/market-load-test.js
//
// 通过标准:
// - P95 < 50ms, P99 < 100ms
// - 错误率 < 0.1%
// - 500 VU, 5min

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 500,
    duration: '5m',
    thresholds: {
        http_req_duration: ['p(95)<50', 'p(99)<100'],
        http_req_failed: ['rate<0.001'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // TODO: 实现行情查询场景
    // 1. 单只股票行情 (GET /api/v1/market/stocks/{code}/quote)
    // 2. 批量行情 (POST /api/v1/market/stocks/batch-quotes)
    // 3. K线数据 (GET /api/v1/market/stocks/{code}/kline)
    // 4. 检查 X-Cache-Status 响应头
    sleep(0.5);
}
