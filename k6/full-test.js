import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const idempotentSuccessRate = new Rate('idempotent_success_rate');
const rateLimitSuccessRate = new Rate('ratelimit_success_rate');
const idempotentResponseTime = new Trend('idempotent_response_time');
const rateLimitResponseTime = new Trend('ratelimit_response_time');

// 测试配置
export const options = {
    scenarios: {
        // 场景1: 幂等接口测试
        idempotent_test: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 1000,
            maxDuration: '60s',
            exec: 'idempotentTest',
            tags: { test_type: 'idempotent' },
        },
        // 场景2: 限流接口测试
        ratelimit_test: {
            executor: 'constant-arrival-rate',
            rate: 300,
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            exec: 'rateLimitTest',
            startTime: '65s',  // 在幂等测试完成后开始
            tags: { test_type: 'ratelimit' },
        },
    },
    thresholds: {
        'idempotent_success_rate': ['rate>=0.999'],
        'idempotent_response_time': ['p(95)<500'],
        'ratelimit_response_time': ['p(95)<200'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const IDEMPOTENT_KEY = 'k6-full-test-key-' + Date.now();

// 幂等接口测试
export function idempotentTest() {
    const payload = JSON.stringify({
        clientRequestId: 'k6-full-test-req-001',
        userId: 10001,
        amount: 199.99,
        remark: 'k6综合压测订单'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': IDEMPOTENT_KEY,
        },
    };

    const startTime = Date.now();
    const response = http.post(`${BASE_URL}/demo/idempotent`, payload, params);
    const duration = Date.now() - startTime;
    
    idempotentResponseTime.add(duration);

    const isSuccess = check(response, {
        '[Idempotent] status is 200': (r) => r.status === 200,
        '[Idempotent] code is 00000': (r) => {
            try {
                return JSON.parse(r.body).code === '00000';
            } catch (e) {
                return false;
            }
        },
    });

    idempotentSuccessRate.add(isSuccess ? 1 : 0);
    sleep(0.01);
}

// 限流接口测试
export function rateLimitTest() {
    const startTime = Date.now();
    const response = http.get(`${BASE_URL}/demo/ratelimit`);
    const duration = Date.now() - startTime;
    
    rateLimitResponseTime.add(duration);

    let body;
    try {
        body = JSON.parse(response.body);
    } catch (e) {
        rateLimitSuccessRate.add(0);
        return;
    }

    // 00000 或 A0500 都是正确的响应
    const isValidResponse = body.code === '00000' || body.code === 'A0500';
    
    check(response, {
        '[RateLimit] status is 200': (r) => r.status === 200,
        '[RateLimit] valid response code': () => isValidResponse,
    });

    rateLimitSuccessRate.add(isValidResponse ? 1 : 0);
}

export function handleSummary(data) {
    console.log('\n============ 综合压测报告 ============');
    console.log('\n--- 幂等接口 ---');
    if (data.metrics.idempotent_success_rate) {
        console.log(`成功率: ${(data.metrics.idempotent_success_rate.values.rate * 100).toFixed(2)}%`);
    }
    if (data.metrics.idempotent_response_time) {
        console.log(`P95响应时间: ${data.metrics.idempotent_response_time.values['p(95)'].toFixed(2)}ms`);
    }
    
    console.log('\n--- 限流接口 ---');
    if (data.metrics.ratelimit_success_rate) {
        console.log(`成功率: ${(data.metrics.ratelimit_success_rate.values.rate * 100).toFixed(2)}%`);
    }
    if (data.metrics.ratelimit_response_time) {
        console.log(`P95响应时间: ${data.metrics.ratelimit_response_time.values['p(95)'].toFixed(2)}ms`);
    }
    
    console.log('\n=====================================\n');
    
    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
