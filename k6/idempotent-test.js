import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const idempotentSuccess = new Counter('idempotent_success');
const idempotentFailed = new Counter('idempotent_failed');
const responseTime = new Trend('response_time');

// 测试配置
export const options = {
    scenarios: {
        // 场景1: 1000并发使用相同的幂等键
        same_key_test: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 1000,
            maxDuration: '60s',
        },
    },
    thresholds: {
        'success_rate': ['rate>=0.999'],  // 成功率 >= 99.9%
        'http_req_duration': ['p(95)<500'], // 95%请求在500ms内完成
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const IDEMPOTENT_KEY = 'k6-test-key-' + Date.now();

// 存储首次响应用于比对
let firstResponse = null;

export default function () {
    const payload = JSON.stringify({
        clientRequestId: 'k6-client-req-001',
        userId: 10001,
        amount: 99.99,
        remark: 'k6压测订单'
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
    
    responseTime.add(duration);

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
        'response has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.data !== null && body.data !== undefined;
            } catch (e) {
                return false;
            }
        },
        'response has traceId': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.traceId !== null && body.traceId !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (isSuccess) {
        idempotentSuccess.add(1);
        successRate.add(1);
        
        // 验证幂等性：所有响应的orderNo应该相同
        try {
            const body = JSON.parse(response.body);
            if (body.data && body.data.orderNo) {
                if (firstResponse === null) {
                    firstResponse = body.data.orderNo;
                    console.log(`First response orderNo: ${firstResponse}`);
                } else {
                    // 检查orderNo是否一致
                    if (body.data.orderNo !== firstResponse) {
                        console.error(`Idempotency violation! Expected: ${firstResponse}, Got: ${body.data.orderNo}`);
                        idempotentFailed.add(1);
                    }
                }
            }
        } catch (e) {
            // ignore
        }
    } else {
        idempotentFailed.add(1);
        successRate.add(0);
        console.error(`Request failed: ${response.status} - ${response.body}`);
    }

    sleep(0.01); // 10ms间隔
}

export function handleSummary(data) {
    console.log('\n========== 幂等接口压测报告 ==========');
    console.log(`总请求数: ${data.metrics.http_reqs.values.count}`);
    console.log(`成功率: ${(data.metrics.success_rate.values.rate * 100).toFixed(2)}%`);
    console.log(`平均响应时间: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`P95响应时间: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`P99响应时间: ${data.metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
    console.log('=====================================\n');
    
    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
