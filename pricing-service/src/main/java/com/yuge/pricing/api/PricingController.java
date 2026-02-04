package com.yuge.pricing.api;

import com.yuge.platform.infra.common.Result;
import com.yuge.pricing.api.dto.*;
import com.yuge.pricing.application.PricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 定价控制器
 */
@RestController
@RequestMapping("/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    /**
     * 试算（不锁定）
     */
    @PostMapping("/quote")
    public Result<QuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        return Result.success(pricingService.quote(request));
    }

    /**
     * 锁价
     */
    @PostMapping("/lock")
    public Result<LockResponse> lock(@Valid @RequestBody LockRequest request) {
        return Result.success(pricingService.lock(request));
    }

    /**
     * 查询价格锁
     */
    @GetMapping("/lock/{priceLockNo}")
    public Result<LockResponse> getPriceLock(@PathVariable String priceLockNo) {
        return Result.success(pricingService.getPriceLock(priceLockNo));
    }

    /**
     * 验证价格锁签名
     */
    @GetMapping("/lock/{priceLockNo}/verify")
    public Result<Boolean> verifySignature(@PathVariable String priceLockNo,
                                           @RequestParam String signature) {
        return Result.success(pricingService.verifySignature(priceLockNo, signature));
    }

    /**
     * 使用价格锁（下单时调用）
     */
    @PostMapping("/lock/{priceLockNo}/use")
    public Result<LockResponse> usePriceLock(@PathVariable String priceLockNo,
                                             @RequestParam String orderNo,
                                             @RequestParam String signature) {
        return Result.success(pricingService.usePriceLock(priceLockNo, orderNo, signature));
    }

    /**
     * 取消价格锁
     */
    @PostMapping("/lock/{priceLockNo}/cancel")
    public Result<Void> cancelPriceLock(@PathVariable String priceLockNo) {
        pricingService.cancelPriceLock(priceLockNo);
        return Result.success(null);
    }
}
