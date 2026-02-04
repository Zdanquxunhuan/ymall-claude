import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// 自定义指标
const orderCreatedCounter = new Counter('orders_created');
const orderCreatedRate = new Rate('order_create_success_rate');
const orderCreateDuration = new Trend('order_create_duration');
const outboxDeliveryRate = new Rate('outbox_delivery_success_rate');

// 配置
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';

// 测试场景配置
export const options = {
    scenarios: {
        // 场景1: 订单创建压测
        order_creation: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 },   // 预热
                { duration: '30s', target: 50 },   // 加压
                { duration: '30s', target: 100 },  // 峰值
                { duration: '10s', target: 0 },    // 降压
            ],
            gracefulRampDown: '5s',
            exec: 'createOrderTest',
        },
        // 场景2: 稳定性测试
        stability_test: {
            executor: 'constant-vus',
            vus: 20,
            duration: '60s',
            startTime: '90s',  // 在场景1结束后开始
            exec: 'stabilityTest',
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],
        'order_create_success_rate': ['rate>0.99'],
        'http_req_failed': ['rate<0.01'],
    },
};

// 生成唯一的客户端请求ID
function generateClientRequestId() {
    return `k6-${Date.now()}-${randomString(8)}`;
}

// 生成随机用户ID
function generateUserId() {
    return Math.floor(Math.random() * 100000) + 10000;
}

// 生成随机订单项
function generateOrderItems() {
    const itemCount = Math.floor(Math.random() * 3) + 1;
    const items = [];
    for (let i = 0; i < itemCount; i++) {
        items.push({
            skuId: Math.floor(Math.random() * 10000) + 1000,
            qty: Math.floor(Math.random() * 5) + 1,
            titleSnapshot: `Test Product ${i + 1}`,
            priceSnapshot: Math.floor(Math.random() * 1000) + 10,
        });
    }
    return items;
}

// 场景1: 订单创建测试
export function createOrderTest() {
    const clientRequestId = generateClientRequestId();
    const userId = generateUserId();
    const traceId = `k6-trace-${randomString(16)}`;

    const payload = JSON.stringify({
        clientRequestId: clientRequestId,
        userId: userId,
        items: generateOrderItems(),
        remark: 'K6 Outbox Relay Test',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Trace-Id': traceId,
        },
        tags: { name: 'CreateOrder' },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/api/orders`, payload, params);
    const duration = Date.now() - startTime;

    orderCreateDuration.add(duration);

    const success = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has code': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.code !== undefined;
            } catch (e) {
                return false;
            }
        },
        'response code is success': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.code === '00000';
            } catch (e) {
                return false;
            }
        },
        'response has orderNo': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data && body.data.orderNo;
            } catch (e) {
                return false;
            }
        },
        'response has traceId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.traceId !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (success) {
        orderCreatedCounter.add(1);
        orderCreatedRate.add(1);
    } else {
        orderCreatedRate.add(0);
        console.log(`Order creation failed: ${response.status} - ${response.body}`);
    }

    sleep(0.1); // 100ms 间隔
}

// 场景2: 稳定性测试
export function stabilityTest() {
    group('Stability Test', function () {
        // 创建订单
        const clientRequestId = generateClientRequestId();
        const userId = generateUserId();

        const payload = JSON.stringify({
            clientRequestId: clientRequestId,
            userId: userId,
            items: [
                {
                    skuId: 1001,
                    qty: 1,
                    titleSnapshot: 'Stability Test Product',
                    priceSnapshot: 99.99,
                },
            ],
            remark: 'Stability Test',
        });

        const params = {
            headers: {
                'Content-Type': 'application/json',
                'X-Trace-Id': `stability-${randomString(16)}`,
            },
            tags: { name: 'StabilityCreateOrder' },
        };

        const response = http.post(`${BASE_URL}/api/orders`, payload, params);

        const success = check(response, {
            'stability: status is 200': (r) => r.status === 200,
            'stability: order created': (r) => {
                try {
                    const body = JSON.parse(r.body);
                    return body.code === '00000' && body.data && body.data.orderNo;
                } catch (e) {
                    return false;
                }
            },
        });

        if (success) {
            outboxDeliveryRate.add(1);
        } else {
            outboxDeliveryRate.add(0);
        }

        sleep(0.5); // 500ms 间隔
    });
}

// 幂等性测试
export function idempotencyTest() {
    const clientRequestId = generateClientRequestId();
    const userId = generateUserId();
    const traceId = `idempotent-${randomString(16)}`;

    const payload = JSON.stringify({
        clientRequestId: clientRequestId,
        userId: userId,
        items: [
            {
                skuId: 2001,
                qty: 1,
                titleSnapshot: 'Idempotency Test Product',
                priceSnapshot: 199.99,
            },
        ],
        remark: 'Idempotency Test',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Trace-Id': traceId,
        },
        tags: { name: 'IdempotencyTest' },
    };

    // 第一次请求
    const response1 = http.post(`${BASE_URL}/api/orders`, payload, params);
    
    // 第二次请求（相同的 clientRequestId + userId）
    const response2 = http.post(`${BASE_URL}/api/orders`, payload, params);

    check(response1, {
        'first request success': (r) => r.status === 200,
    });

    check(response2, {
        'second request success': (r) => r.status === 200,
        'idempotent: same orderNo': (r) => {
            try {
                const body1 = JSON.parse(response1.body);
                const body2 = JSON.parse(r.body);
                return body1.data.orderNo === body2.data.orderNo;
            } catch (e) {
                return false;
            }
        },
    });

    sleep(1);
}

// 默认函数
export default function () {
    createOrderTest();
}

// 测试结束后的汇总
export function handleSummary(data) {
    const summary = {
        'Total Orders Created': data.metrics.orders_created ? data.metrics.orders_created.values.count : 0,
        'Order Create Success Rate': data.metrics.order_create_success_rate ? 
            (data.metrics.order_create_success_rate.values.rate * 100).toFixed(2) + '%' : 'N/A',
        'Avg Order Create Duration': data.metrics.order_create_duration ? 
            data.metrics.order_create_duration.values.avg.toFixed(2) + 'ms' : 'N/A',
        'P95 Order Create Duration': data.metrics.order_create_duration ? 
            data.metrics.order_create_duration.values['p(95)'].toFixed(2) + 'ms' : 'N/A',
        'P99 Order Create Duration': data.metrics.order_create_duration ? 
            data.metrics.order_create_duration.values['p(99)'].toFixed(2) + 'ms' : 'N/A',
        'HTTP Request Failed Rate': data.metrics.http_req_failed ? 
            (data.metrics.http_req_failed.values.rate * 100).toFixed(2) + '%' : 'N/A',
    };

    console.log('\n========== Outbox Relay Test Summary ==========');
    for (const [key, value] of Object.entries(summary)) {
        console.log(`${key}: ${value}`);
    }
    console.log('================================================\n');

    return {
        'stdout': JSON.stringify(summary, null, 2),
        'k6/outbox-relay-test-result.json': JSON.stringify(data, null, 2),
    };
}
