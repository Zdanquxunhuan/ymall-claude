package com.yuge.demo.api;

import com.yuge.demo.api.dto.CreateOrderRequest;
import com.yuge.demo.api.dto.OrderResponse;
import com.yuge.demo.application.DemoService;
import com.yuge.platform.infra.common.Result;
import com.yuge.platform.infra.idempotent.Idempotent;
import com.yuge.platform.infra.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Demo控制器
 * 演示幂等、限流功能
 */
@Slf4j
@RestController
@RequestMapping("/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;

    /**
     * 幂等接口演示
     * 同一个 idempotency_key 重复请求返回相同结果
     * 
     * 幂等键来源：请求头 X-Idempotency-Key
     */
    @PostMapping("/idempotent")
    @Idempotent(
        keySource = Idempotent.KeySource.HEADER,
        keyField = "X-Idempotency-Key",
        prefix = "demo:order",
        timeout = 24,
        storeResult = true
    )
    public Result<OrderResponse> createOrderWithHeader(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("Creating order with header idempotency, userId={}, amount={}", 
                request.getUserId(), request.getAmount());
        OrderResponse response = demoService.createOrder(request);
        return Result.success(response);
    }

    /**
     * 幂等接口演示（从Body获取幂等键）
     * 使用 clientRequestId 作为幂等键
     */
    @PostMapping("/idempotent/body")
    @Idempotent(
        keySource = Idempotent.KeySource.BODY,
        keyField = "clientRequestId",
        prefix = "demo:order",
        timeout = 24,
        storeResult = true
    )
    public Result<OrderResponse> createOrderWithBody(
            @Valid @RequestBody CreateOrderRequest request) {
        log.info("Creating order with body idempotency, clientRequestId={}, userId={}", 
                request.getClientRequestId(), request.getUserId());
        OrderResponse response = demoService.createOrder(request);
        return Result.success(response);
    }

    /**
     * 限流接口演示
     * 阈值：200 QPS，令牌桶容量：200
     * 超出返回错误码 A0500
     */
    @GetMapping("/ratelimit")
    @RateLimit(
        key = "demo:ratelimit",
        qps = 200,
        capacity = 200,
        dimension = RateLimit.Dimension.API,
        message = "请求过于频繁，请稍后重试"
    )
    public Result<String> rateLimitDemo() {
        return Result.success("Request processed successfully at " + System.currentTimeMillis());
    }

    /**
     * 按用户限流演示
     * 每个用户 10 QPS
     */
    @GetMapping("/ratelimit/user")
    @RateLimit(
        key = "demo:ratelimit:user",
        qps = 10,
        capacity = 20,
        dimension = RateLimit.Dimension.API_USER,
        userIdSource = RateLimit.UserIdSource.HEADER,
        userIdField = "X-User-Id",
        message = "用户请求过于频繁，请稍后重试"
    )
    public Result<String> rateLimitByUser(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return Result.success("User " + userId + " request processed at " + System.currentTimeMillis());
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("OK");
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/order/{orderNo}")
    public Result<OrderResponse> getOrder(@PathVariable String orderNo) {
        OrderResponse response = demoService.getOrderByNo(orderNo);
        return Result.success(response);
    }
}
