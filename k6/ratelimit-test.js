import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 自定义指标
const successRate = new Rate('success_rate');
const rateLimitedCount = new Counter('rate_limited_count');
const passedCount = new Counter('passed_count');
const responseTime = new Trend('response_time');

// 测试配置
// 目标：验证200 QPS限流，超出部分返回A0500
export const options = {
    scenarios: {
        // 场景：持续高于限流阈值的请求
        ratelimit_test: {
            executor: 'constant-arrival-rate',
            rate: 300,  // 每秒300个请求，超过200 QPS阈值
            timeUnit: '1s',
            duration: '30s',
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
    thresholds: {
        'http_req_duration': ['p(95)<200'],  // 95%请求在200ms内完成
        'rate_limited_count': ['count>0'],    // 必须有请求被限流
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export default function () {
    const startTime = Date.now();
    const response = http.get(`${BASE_URL}/demo/ratelimit`);
    const duration = Date.now() - startTime;
    
    responseTime.add(duration);

    let body;
    try {
        body = JSON.parse(response.body);
    } catch (e) {
        console.error(`Failed to parse response: ${response.body}`);
        return;
    }

    // 检查响应
    const isSuccess = check(response, {
        'status is 200': (r) => r.status === 200,
        'response has code': (r) => body.code !== undefined,
    });

    if (body.code === '00000') {
        // 请求通过
        passedCount.add(1);
        successRate.add(1);
    } else if (body.code === 'A0500') {
        // 被限流
        rateLimitedCount.add(1);
        successRate.add(1);  // 限流也是正确的行为
        
        // 验证限流响应
        check(response, {
            'rate limit error code is A0500': () => body.code === 'A0500',
            'rate limit has message': () => body.message !== undefined,
            'rate limit has traceId': () => body.traceId !== undefined,
        });
    } else {
   // 其他错误
        successRate.add(0);
        console.error(`Unexpected response: ${response.body}`);
    }
}

export function handleSummary(data) {
    const totalRequests = data.metrics.http_reqs.values.count;
    const passedRequests = data.metrics.passed_count ? data.metrics.passed_count.values.count : 0;
    const rateLimitedRequests = data.metrics.rate_limited_count ? data.metrics.rate_limited_count.values.count : 0;
    
    console.log('\n========== 限流接口压测报告 ==========');
    console.log(`总请求数: ${totalRequests}`);
    console.log(`通过请求数: ${passedRequests}`);
    console.log(`被限流请求数: ${rateLimitedRequests}`);
    console.log(`限流比例: ${((rateLimitedRequests / totalRequests) * 100).toFixed(2)}%`);
    console.log(`平均响应时间: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`P95响应时间: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log('=====================================\n');
    
    // 验证：在300 QPS下，约有33%的请求应该被限流 (100/300)
    const expectedRateLimitRatio = (300 - 200) / 300;  // 约33%
    const actualRateLimitRatio = rateLimitedRequests / totalRequests;
    
    if (actualRateLimitRatio > 0.1) {
        console.log('✓ 限流功能正常工作');
    } else {
        console.log('✗ 警告：限流可能未生效');
    }
    
    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
