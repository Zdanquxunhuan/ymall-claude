package com.yuge.payment.api.controller;

import com.yuge.payment.api.dto.*;
import com.yuge.payment.application.RefundService;
import com.yuge.platform.infra.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 退款控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    /**
     * 创建退款单
     */
    @PostMapping
    public Result<RefundResponse> createRefund(@RequestBody CreateRefundRequest request) {
        log.info("[RefundController] createRefund, orderNo={}, asNo={}", request.getOrderNo(), request.getAsNo());
        RefundResponse response = refundService.createRefund(request);
        return Result.success(response);
    }

    /**
     * 查询退款单
     */
    @GetMapping("/{refundNo}")
    public Result<RefundResponse> getRefund(@PathVariable String refundNo) {
        log.info("[RefundController] getRefund, refundNo={}", refundNo);
        RefundResponse response = refundService.getRefund(refundNo);
        return Result.success(response);
    }

    /**
     * 根据订单号查询退款单
     */
    @GetMapping("/order/{orderNo}")
    public Result<RefundResponse> getRefundByOrderNo(@PathVariable String orderNo) {
        log.info("[RefundController] getRefundByOrderNo, orderNo={}", orderNo);
        RefundResponse response = refundService.getRefundByOrderNo(orderNo);
        return Result.success(response);
    }

    /**
     * 模拟退款回调
     */
    @PostMapping("/callback/mock")
    public Result<RefundCallbackResponse> handleMockRefundCallback(@RequestBody MockRefundCallbackRequest request) {
        log.info("[RefundController] handleMockRefundCallback, refundNo={}", request.getRefundNo());
        RefundCallbackResponse response = refundService.handleMockRefundCallback(request);
        return Result.success(response);
    }

    /**
     * 生成签名（供测试使用）
     */
    @GetMapping("/signature")
    public Result<String> generateSignature(@RequestParam String refundNo,
                                            @RequestParam String callbackStatus,
                                            @RequestParam String timestamp,
                                            @RequestParam String nonce) {
        String signature = refundService.generateSignature(refundNo, callbackStatus, timestamp, nonce);
        return Result.success(signature);
    }
}
