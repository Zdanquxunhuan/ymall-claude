package com.yuge.payment.api.controller;

import com.yuge.payment.api.dto.*;
import com.yuge.payment.application.PaymentService;
import com.yuge.platform.infra.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付控制器
 */
@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付单（按order_no幂等）
     */
    @PostMapping
    public Result<PaymentResponse> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        log.info("[PaymentController] Creating payment, orderNo={}, amount={}", 
                request.getOrderNo(), request.getAmount());
        PaymentResponse response = paymentService.createPayment(request);
        return Result.success(response);
    }

    /**
     * 查询支付单
     */
    @GetMapping("/{payNo}")
    public Result<PaymentResponse> getPayment(@PathVariable String payNo) {
        PaymentResponse response = paymentService.getPayment(payNo);
        return Result.success(response);
    }

    /**
     * 根据订单号查询支付单
     */
    @GetMapping("/order/{orderNo}")
    public Result<PaymentResponse> getPaymentByOrderNo(@PathVariable String orderNo) {
        PaymentResponse response = paymentService.getPaymentByOrderNo(orderNo);
        return Result.success(response);
    }

    /**
     * 模拟第三方支付回调（必须验签 + 幂等）
     */
    @PostMapping("/mock-callback")
    public Result<CallbackResponse> mockCallback(@Valid @RequestBody MockCallbackRequest request) {
        log.info("[PaymentController] Received mock callback, payNo={}, status={}", 
                request.getPayNo(), request.getCallbackStatus());
        CallbackResponse response = paymentService.handleMockCallback(request);
        return Result.success(response);
    }

    /**
     * 生成签名（仅供测试使用）
     */
    @GetMapping("/generate-signature")
    public Result<String> generateSignature(
            @RequestParam String payNo,
            @RequestParam String callbackStatus,
            @RequestParam String timestamp,
            @RequestParam String nonce) {
        String signature = paymentService.generateSignature(payNo, callbackStatus, timestamp, nonce);
        return Result.success(signature);
    }
}
