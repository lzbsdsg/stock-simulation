// k6 WebSocket 连接压测脚本
// 运行: k6 run --vus 10000 --duration 5m k6/websocket-load-test.js
//
// 通过标准:
// - 10000 并发 WS 连接
// - 推送延迟 P99 < 500ms
// - 5min 稳定运行

import ws from 'k6/ws';
import { check, sleep } from 'k6';

export const options = {
    vus: 10000,
    duration: '5m',
    thresholds: {
        ws_connecting: ['p(99)<1000'],
    },
};

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/market';

export default function () {
    // TODO: 实现 WS 连接压测
    // 1. 建立 STOMP over SockJS 连接
    // 2. 订阅行情 topic
    // 3. 记录推送延迟
    // 4. 验证背压降级行为
    sleep(1);
}
