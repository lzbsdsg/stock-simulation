// k6 WebSocket 连接压测脚本
// 运行: k6 run --vus 10000 --duration 5m k6/websocket-load-test.js
//
// 通过标准:
// - 10000 并发 WS 连接
// - 推送延迟 P99 < 500ms
// - 5min 稳定运行

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const options = {
    vus: 10000,
    duration: '5m',
    thresholds: {
        ws_connecting: ['p(99)<1000'],
        ws_push_latency_ms: ['p(99)<500'],
        ws_latency_samples_total: ['count>0'],
    },
};

const WS_URL = __ENV.WS_URL || 'ws://localhost:8080/ws/market/websocket';
const ACCESS_TOKEN = __ENV.WS_TOKEN || '';
const TARGET_CODE = __ENV.TARGET_CODE || 'bj920000';
const WS_SESSION_MS = Number(__ENV.WS_SESSION_MS || '5000');
const DEBUG_WS_PAYLOAD = (__ENV.DEBUG_WS_PAYLOAD || 'false').toLowerCase() === 'true';

const wsPushLatency = new Trend('ws_push_latency_ms');
const wsReceivedTotal = new Counter('ws_received_total');
const wsLatencySamplesTotal = new Counter('ws_latency_samples_total');
const wsLatencyMissingTotal = new Counter('ws_latency_missing_total');
const wsLatencyParseErrorTotal = new Counter('ws_latency_parse_error_total');
let printedDebugSample = false;

export default function () {
    const headers = ACCESS_TOKEN ? { Authorization: `Bearer ${ACCESS_TOKEN}` } : {};
    const connectRes = ws.connect(WS_URL, { headers }, function (socket) {
        socket.on('open', function () {
            socket.send('CONNECT\naccept-version:1.2\nhost:localhost\n\n\u0000');
        });

        socket.on('message', function (msg) {
            const textMsg = normalizeWsMessage(msg);
            if (!textMsg) {
                return;
            }

            const stompFrames = unwrapSockJsFrames(textMsg);
            if (stompFrames.length === 0) {
                return;
            }

            for (const frame of stompFrames) {
                processStompFrame(frame, socket);
            }
        });

        socket.setTimeout(function () {
            socket.close();
        }, WS_SESSION_MS);
    });

    check(connectRes, {
        'ws connect success': (r) => r && r.status === 101,
    });

    sleep(1);
}

function processStompFrame(textMsg, socket) {
    if (!textMsg) {
        return;
    }

    // STOMP CONNECTED frame
    if (textMsg.startsWith('CONNECTED')) {
        socket.send(`SUBSCRIBE\nid:sub-${__VU}-${__ITER}\ndestination:/topic/market/quote/${TARGET_CODE}\n\n\u0000`);
        return;
    }

    // STOMP MESSAGE frame
    if (!textMsg.startsWith('MESSAGE')) {
        return;
    }

    wsReceivedTotal.add(1);
    const bodyIdx = textMsg.indexOf('\n\n');
    if (bodyIdx < 0) {
        if (DEBUG_WS_PAYLOAD && !printedDebugSample) {
            printedDebugSample = true;
            console.log(`WS frame without body delimiter: ${textMsg}`);
        }
        return;
    }

    const payloadRaw = textMsg.substring(bodyIdx + 2).replace(/\u0000/g, '').trim();
    if (!payloadRaw) {
        if (DEBUG_WS_PAYLOAD && !printedDebugSample) {
            printedDebugSample = true;
            console.log(`WS frame with empty body: ${textMsg}`);
        }
        return;
    }

    const ts = extractPushTimestamp(payloadRaw);
    if (Number.isNaN(ts)) {
        wsLatencyMissingTotal.add(1);
        if (DEBUG_WS_PAYLOAD && !printedDebugSample) {
            printedDebugSample = true;
            console.log(`WS raw payload sample: ${payloadRaw}`);
        }
        return;
    }

    wsPushLatency.add(Date.now() - ts);
    wsLatencySamplesTotal.add(1);
}

function parseTimestamp(value) {
    if (typeof value === 'number') {
        return value;
    }

    // 兼容 Jackson LocalDateTime 数组序列化: [yyyy,MM,dd,HH,mm,ss,nano]
    if (Array.isArray(value) && value.length >= 6) {
        const year = Number(value[0]);
        const month = Number(value[1]);
        const day = Number(value[2]);
        const hour = Number(value[3]);
        const minute = Number(value[4]);
        const second = Number(value[5]);
        const millisecond = value.length >= 7 ? Math.floor(Number(value[6]) / 1_000_000) : 0;
        if ([year, month, day, hour, minute, second, millisecond].some((x) => Number.isNaN(x))) {
            return Number.NaN;
        }
        const dt = new Date(year, month - 1, day, hour, minute, second, millisecond);
        return dt.getTime();
    }

    if (typeof value !== 'string') {
        return Number.NaN;
    }

    if (/^\d{10,17}$/.test(value)) {
        const numericTs = Number(value);
        if (!Number.isNaN(numericTs)) {
            return numericTs;
        }
    }

    // 兼容后端默认格式: yyyy-MM-dd HH:mm:ss
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) {
        return Date.parse(value.replace(' ', 'T') + '+08:00');
    }

    return Date.parse(value);
}

function extractPushTimestamp(payloadRaw) {
    try {
        const payload = JSON.parse(payloadRaw);
        const rawTs = findTimestampInObject(payload);
        const ts = parseTimestamp(rawTs);
        if (!Number.isNaN(ts)) {
            return ts;
        }
    } catch (_) {
        wsLatencyParseErrorTotal.add(1);
    }

    // 兼容非 JSON 文本: wsPushTsMillis=1711260000000 或 "wsPushTsMillis":1711260000000
    const match = payloadRaw.match(/wsPushTsMillis\s*[=:]\s*"?(\d{10,17})"?/i)
        || payloadRaw.match(/"wsPushTsMillis"\s*:\s*"?(\d{10,17})"?/i)
        || payloadRaw.match(/timestamp\s*[=:]\s*"?(\d{10,17})"?/i)
        || payloadRaw.match(/"timestamp"\s*:\s*"?(\d{10,17})"?/i);

    if (match && match[1]) {
        const ts = Number(match[1]);
        if (!Number.isNaN(ts)) {
            return ts;
        }
    }

    return Number.NaN;
}

function findTimestampInObject(payload) {
    if (!payload || typeof payload !== 'object') {
        return undefined;
    }

    const direct = payload.wsPushTsMillis ?? payload.timestamp ?? payload.ts ?? payload.eventTime;
    if (direct !== undefined && direct !== null) {
        return direct;
    }

    const nested = payload.payload ?? payload.data ?? payload.quote ?? payload.body;
    if (nested && typeof nested === 'object') {
        return findTimestampInObject(nested);
    }

    return undefined;
}

function normalizeWsMessage(msg) {
    if (typeof msg === 'string') {
        return msg;
    }

    // 兼容 k6 二进制消息（ArrayBuffer / TypedArray）
    if (msg instanceof ArrayBuffer) {
        return decodeUtf8(new Uint8Array(msg));
    }

    if (msg && msg.buffer instanceof ArrayBuffer) {
        const view = new Uint8Array(msg.buffer, msg.byteOffset || 0, msg.byteLength || msg.length || 0);
        return decodeUtf8(view);
    }

    return '';
}

function decodeUtf8(uint8arr) {
    if (!uint8arr || uint8arr.length === 0) {
        return '';
    }
    if (typeof TextDecoder !== 'undefined') {
        return new TextDecoder('utf-8').decode(uint8arr);
    }

    let out = '';
    for (let i = 0; i < uint8arr.length; i += 1) {
        out += String.fromCharCode(uint8arr[i]);
    }
    return out;
}

function unwrapSockJsFrames(textMsg) {
    if (!textMsg) {
        return [];
    }

    // SockJS open/heartbeat/close frames
    if (textMsg === 'o' || textMsg === 'h' || textMsg.startsWith('c[')) {
        return [];
    }

    // SockJS message frame: a["..."]
    if (textMsg.startsWith('a[')) {
        try {
            const arr = JSON.parse(textMsg.slice(1));
            if (Array.isArray(arr)) {
                return arr.filter((x) => typeof x === 'string');
            }
        } catch (_) {
            wsLatencyParseErrorTotal.add(1);
        }
        return [];
    }

    return [textMsg];
}
