package com.yuge.order.api;

import com.yuge.order.api.dto.CancelOrderRequest;
import com.yuge.order.api.dto.CreateOrderRequest;
import com.yuge.order.api.dto.OrderResponse;
import com.yuge.order.application.OrderService;
import com.yuge.platform.infra.common.Result;
import com.yuge.platform.infra.idempotent.Idempotent;
import com.yuge.platform.infra.ratelimit.RateLimit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单（幂等）
     * 幂等键：clientRequestId（从Body获取）
     */
    @PostMapping
    @RateLimit(key = "order:create", qps = 500, capacity = 600)
    @Idempotent(
        keySource = Idempotent.KeySource.BODY,
        keyField = "clientRequestId",
        prefix = "order:create",
        timeout = 24,
        storeResult = true
    )
    public Result<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("[OrderController] Creating order, userId={}, clientRequestId={}, itemCount={}",
                request.getUserId(), request.getClientRequestId(), request.getItems().size());
        OrderResponse response = orderService.createOrder(request);
        return Result.success(response);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    @RateLimit(key = "order:query", qps = 1000, capacity = 1200)
    public Result<OrderResponse> getOrder(@PathVariable String orderNo) {
        log.info("[OrderController] Getting order, orderNo={}", orderNo);
        OrderResponse response = orderService.getOrder(orderNo);
        return Result.success(response);
    }

    /**
     * 取消订单（幂等）
     * 幂等键：orderNo + clientRequestId（从Header获取）
     */
    @PostMapping("/{orderNo}/cancel")
    @RateLimit(key = "order:cancel", qps = 200, capacity = 300)
    @Idempotent(
        keySource = Idempotent.KeySource.HEADER,
        keyField = "X-Idempotency-Key",
        prefix = "order:cancel",
        timeout = 24,
        storeResult = true
    )
    public Result<OrderResponse> cancelOrder(
            @PathVariable String orderNo,
            @RequestBody(required = false) CancelOrderRequest request) {
        log.info("[OrderController] Canceling order, orderNo={}", orderNo);
        if (request == null) {
            request = new CancelOrderRequest();
        }
        OrderResponse response = orderService.cancelOrder(orderNo, request);
        return Result.success(response);
    }
}
