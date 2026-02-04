import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const idempotentSuccessRate = new Rate('idempotent_success_rate');
const cancelSuccessRate = new Rate('cancel_success_rate');
const invalidCancelCount = new Counter('invalid_cancel_count');

// 测试配置
export const options = {
    scenarios: {
        // 场景1: 幂等创建测试 - 1000并发相同key
        idempotent_create: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 1000,
            maxDuration: '60s',
            exec: 'testIdempotentCreate',
            tags: { test_type: 'idempotent_create' },
        },
        // 场景2: 重复取消测试
        duplicate_cancel: {
            executor: 'shared-iterations',
            vus: 50,
            iterations: 500,
            maxDuration: '60s',
            exec: 'testDuplicateCancel',
            startTime: '65s',
            tags: { test_type: 'duplicate_cancel' },
        },
        // 场景3: 非法状态取消测试
        invalid_cancel: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 100,
            maxDuration: '30s',
            exec: 'testInvalidCancel',
            startTime: '130s',
            tags: { test_type: 'invalid_cancel' },
        },
    },
    thresholds: {
        'idempotent_success_rate': ['rate>=0.999'],
        'cancel_success_rate': ['rate>=0.99'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';

// 共享的幂等键（用于测试幂等创建）
const SHARED_IDEMPOTENT_KEY = 'k6-idempotent-test-' + Date.now();
let firstOrderNo = null;

// 测试1: 幂等创建 - 1000并发使用相同clientRequestId
export function testIdempotentCreate() {
    const payload = JSON.stringify({
        clientRequestId: SHARED_IDEMPOTENT_KEY,
        userId: 99999,
        items: [
            {
                skuId: 1001,
                qty: 1,
                title: '幂等测试商品',
                price: '99.99'
            }
        ],
        remark: '幂等创建测试'
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const response = http.post(`${BASE_URL}/orders`, payload, params);

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

    if (isSuccess) {
        idempotentSuccessRate.add(1);
        
        // 验证所有响应返回相同的orderNo
        try {
            const body = JSON.parse(response.body);
            if (body.data && body.data.orderNo) {
                if (firstOrderNo === null) {
                    firstOrderNo = body.data.orderNo;
                    console.log(`First orderNo: ${firstOrderNo}`);
                } else if (body.data.orderNo !== firstOrderNo) {
                    console.error(`Idempotency violation! Expected: ${firstOrderNo}, Got: ${body.data.orderNo}`);
                    idempotentSuccessRate.add(0);
                }
            }
        } catch (e) {
            // ignore
        }
    } else {
        idempotentSuccessRate.add(0);
        console.error(`Idempotent create failed: ${response.body}`);
    }

    sleep(0.01);
}

// 测试2: 重复取消 - 同一订单多次取消
export function testDuplicateCancel() {
    // 先创建一个订单
    const clientRequestId = `cancel-test-${Date.now()}-${__VU}-${__ITER}`;
    const createPayload = JSON.stringify({
        clientRequestId: clientRequestId,
        userId: 80000 + __VU,
        items: [
            {
                skuId: 2001,
                qty: 1,
                title: '取消测试商品',
                price: '50.00'
            }
        ],
        remark: '取消测试'
    });

    const createResponse = http.post(`${BASE_URL}/orders`, createPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    if (createResponse.status !== 200) {
        cancelSuccessRate.add(0);
        return;
    }

    let orderNo;
    try {
        const body = JSON.parse(createResponse.body);
        orderNo = body.data.orderNo;
    } catch (e) {
        cancelSuccessRate.add(0);
        return;
    }

    // 第一次取消
    const cancelKey = `cancel-key-${orderNo}-${Date.now()}`;
    const cancelResponse1 = http.post(`${BASE_URL}/orders/${orderNo}/cancel`, '{}', {
        headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': cancelKey,
        },
    });

    // 第二次取消（相同key）
    const cancelResponse2 = http.post(`${BASE_URL}/orders/${orderNo}/cancel`, '{}', {
        headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': cancelKey,
        },
    });

    const isSuccess = check(cancelResponse1, {
        '[Cancel] first cancel status 200': (r) => r.status === 200,
        '[Cancel] first cancel code 00000': (r) => {
            try {
                return JSON.parse(r.body).code === '00000';
            } catch (e) {
                return false;
            }
        },
    }) && check(cancelResponse2, {
        '[Cancel] second cancel status 200': (r) => r.status === 200,
        '[Cancel] second cancel code 00000': (r) => {
            try {
                return JSON.parse(r.body).code === '00000';
            } catch (e) {
                return false;
            }
        },
    });

    cancelSuccessRate.add(isSuccess ? 1 : 0);
    sleep(0.05);
}

// 测试3: 非法状态取消 - 已取消订单再次取消（不同key）
export function testInvalidCancel() {
    // 创建订单
    const clientRequestId = `invalid-cancel-${Date.now()}-${__VU}-${__ITER}`;
    const createPayload = JSON.stringify({
        clientRequestId: clientRequestId,
        userId: 70000 + __VU,
        items: [
            {
                skuId: 3001,
                qty: 1,
                title: '非法取消测试商品',
                price: '30.00'
            }
        ],
        remark: '非法取消测试'
    });

    const createResponse = http.post(`${BASE_URL}/orders`, createPayload, {
        headers: { 'Content-Type': 'application/json' },
    });

    if (createResponse.status !== 200) {
        return;
    }

    let orderNo;
    try {
        const body = JSON.parse(createResponse.body);
        orderNo = body.data.orderNo;
    } catch (e) {
        return;
    }

    // 第一次取消
    http.post(`${BASE_URL}/orders/${orderNo}/cancel`, '{}', {
        headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': `key1-${orderNo}`,
        },
    });

    // 第二次取消（不同key，订单已取消）
    // 应该幂等返回，而不是报错
    const cancelResponse = http.post(`${BASE_URL}/orders/${orderNo}/cancel`, '{}', {
        headers: {
            'Content-Type': 'application/json',
            'X-Idempotency-Key': `key2-${orderNo}-${Date.now()}`,
        },
    });

    const isIdempotent = check(cancelResponse, {
        '[InvalidCancel] status 200': (r) => r.status === 200,
        '[InvalidCancel] returns success or already canceled': (r) => {
            try {
                const body = JSON.parse(r.body);
                // 00000 表示幂等返回，A0303 表示状态不合法（也可接受）
                return body.code === '00000' || body.code === 'A0303';
            } catch (e) {
                return false;
            }
        },
    });

    if (!isIdempotent) {
        invalidCancelCount.add(1);
        console.error(`Invalid cancel test failed: ${cancelResponse.body}`);
    }

    sleep(0.1);
}

export function handleSummary(data) {
    console.log('\n============ 订单服务综合测试报告 ============');
    
    if (data.metrics.idempotent_success_rate) {
        console.log(`\n--- 幂等创建测试 ---`);
        console.log(`成功率: ${(data.metrics.idempotent_success_rate.values.rate * 100).toFixed(3)}%`);
    }
    
    if (data.metrics.cancel_success_rate) {
        console.log(`\n--- 重复取消测试 ---`);
        console.log(`成功率: ${(data.metrics.cancel_success_rate.values.rate * 100).toFixed(3)}%`);
    }
    
    if (data.metrics.invalid_cancel_count) {
        console.log(`\n--- 非法状态取消测试 ---`);
        console.log(`失败次数: ${data.metrics.invalid_cancel_count.values.count}`);
    }
    
    console.log('\n==============================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
