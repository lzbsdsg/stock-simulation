// k6 交易接口压测脚本
// 运行: k6 run --vus 200 --duration 5m k6/trade-load-test.js
//
// 通过标准:
// - P95 < 100ms, P99 < 200ms
// - 错误率 < 0.1%
// - 200 VU, 5min

import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 200,
    duration: '5m',
    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<200'],
        http_req_failed: ['rate<0.001'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    // TODO: 实现下单、撤单、查询委托列表等交易场景
    // 1. 登录获取 token
    // 2. 下买单 (POST /api/v1/orders)
    // 3. 查询委托 (GET /api/v1/orders)
    // 4. 撤单 (DELETE /api/v1/orders/{id})
    sleep(1);
}
