import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 自定义指标
const orderCreatedCount = new Counter('orders_created');
const orderFailedCount = new Counter('orders_failed');
const successRate = new Rate('success_rate');
const orderResponseTime = new Trend('order_response_time');

// 测试配置
// 目标：500 RPS 持续 60s，错误率 < 0.1%
export const options = {
    scenarios: {
        create_order: {
            executor: 'constant-arrival-rate',
            rate: 500,  // 每秒500个请求
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 100,
            maxVUs: 200,
        },
    },
    thresholds: {
        'success_rate': ['rate>=0.999'],  // 成功率 >= 99.9%
        'http_req_duration': ['p(95)<500', 'p(99)<1000'],  // P95 < 500ms, P99 < 1s
        'orders_failed': ['count<50'],  // 失败订单数 < 50
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';

// 生成唯一的clientRequestId
function generateClientRequestId() {
    return `k6-${Date.now()}-${uuidv4()}`;
}

// 生成随机用户ID
function generateUserId() {
    return Math.floor(Math.random() * 100000) + 10000;
}

// 生成订单请求
function buildOrderRequest() {
    return {
        clientRequestId: generateClientRequestId(),
        userId: generateUserId(),
        items: [
            {
                skuId: Math.floor(Math.random() * 10000) + 1000,
                qty: Math.floor(Math.random() * 3) + 1,
                title: `测试商品-${Date.now()}`,
                price: (Math.random() * 500 + 10).toFixed(2)
            },
            {
                skuId: Math.floor(Math.random() * 10000) + 1000,
                qty: Math.floor(Math.random() * 2) + 1,
                title: `测试商品2-${Date.now()}`,
                price: (Math.random() * 300 + 20).toFixed(2)
            }
        ],
        remark: 'k6压测订单'
    };
}

export default function () {
    const payload = JSON.stringify(buildOrderRequest());

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
        timeout: '10s',
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/orders`, payload, params);
    const duration = Date.now() - startTime;

    orderResponseTime.add(duration);

    // 检查响应
    const isSuccess = check(response, {
        'status is 200': (r) => r.status === 200,
        'response code is 00000': (r) => {
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
                return body.traceId !== null;
            } catch (e) {
                return false;
            }
        },
    });

    if (isSuccess) {
        orderCreatedCount.add(1);
        successRate.add(1);
    } else {
        orderFailedCount.add(1);
        successRate.add(0);
        
        // 记录失败详情
        console.error(`Order creation failed: status=${response.status}, body=${response.body}`);
    }
}

export function handleSummary(data) {
    const totalRequests = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0;
    const successfulOrders = data.metrics.orders_created ? data.metrics.orders_created.values.count : 0;
    const failedOrders = data.metrics.orders_failed ? data.metrics.orders_failed.values.count : 0;
    const actualSuccessRate = totalRequests > 0 ? (successfulOrders / totalRequests * 100) : 0;

    console.log('\n============ 订单创建压测报告 ============');
    console.log(`测试时长: 60s`);
    console.log(`目标RPS: 500`);
    console.log(`总请求数: ${totalRequests}`);
    console.log(`成功订单数: ${successfulOrders}`);
    console.log(`失败订单数: ${failedOrders}`);
    console.log(`成功率: ${actualSuccessRate.toFixed(3)}%`);
    
    if (data.metrics.http_req_duration) {
        console.log(`平均响应时间: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
        console.log(`P95响应时间: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
        console.log(`P99响应时间: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
    }
    
    console.log('==========================================\n');

    // 验证结果
    if (actualSuccessRate >= 99.9) {
        console.log('✓ 压测通过: 成功率 >= 99.9%');
    } else {
        console.log('✗ 压测失败: 成功率 < 99.9%');
    }

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
