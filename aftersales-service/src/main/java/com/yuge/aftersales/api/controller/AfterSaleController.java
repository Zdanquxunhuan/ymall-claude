package com.yuge.aftersales.api.controller;

import com.yuge.aftersales.api.dto.*;
import com.yuge.aftersales.application.AfterSaleService;
import com.yuge.platform.infra.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 售后控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/aftersales")
@RequiredArgsConstructor
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    /**
     * 申请售后
     */
    @PostMapping("/apply")
    public Result<AfterSaleResponse> applyAfterSale(@RequestBody ApplyAfterSaleRequest request) {
        log.info("[AfterSaleController] applyAfterSale, orderNo={}", request.getOrderNo());
        AfterSaleResponse response = afterSaleService.applyAfterSale(request);
        return Result.success(response);
    }

    /**
     * 审批通过
     */
    @PostMapping("/approve")
    public Result<AfterSaleResponse> approveAfterSale(@RequestBody ApproveAfterSaleRequest request) {
        log.info("[AfterSaleController] approveAfterSale, asNo={}", request.getAsNo());
        AfterSaleResponse response = afterSaleService.approveAfterSale(request);
        return Result.success(response);
    }

    /**
     * 审批拒绝
     */
    @PostMapping("/reject")
    public Result<AfterSaleResponse> rejectAfterSale(@RequestBody RejectAfterSaleRequest request) {
        log.info("[AfterSaleController] rejectAfterSale, asNo={}", request.getAsNo());
        AfterSaleResponse response = afterSaleService.rejectAfterSale(request);
        return Result.success(response);
    }

    /**
     * 取消售后
     */
    @PostMapping("/{asNo}/cancel")
    public Result<AfterSaleResponse> cancelAfterSale(@PathVariable String asNo,
                                                      @RequestParam Long userId) {
        log.info("[AfterSaleController] cancelAfterSale, asNo={}, userId={}", asNo, userId);
        AfterSaleResponse response = afterSaleService.cancelAfterSale(asNo, userId);
        return Result.success(response);
    }

    /**
     * 查询售后单
     */
    @GetMapping("/{asNo}")
    public Result<AfterSaleResponse> getAfterSale(@PathVariable String asNo) {
        log.info("[AfterSaleController] getAfterSale, asNo={}", asNo);
        AfterSaleResponse response = afterSaleService.getAfterSale(asNo);
        return Result.success(response);
    }

    /**
     * 根据订单号查询售后单
     */
    @GetMapping("/order/{orderNo}")
    public Result<AfterSaleResponse> getAfterSaleByOrderNo(@PathVariable String orderNo) {
        log.info("[AfterSaleController] getAfterSaleByOrderNo, orderNo={}", orderNo);
        AfterSaleResponse response = afterSaleService.getAfterSaleByOrderNo(orderNo);
        return Result.success(response);
    }

    /**
     * 查询用户售后单列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<AfterSaleResponse>> getAfterSalesByUserId(@PathVariable Long userId) {
        log.info("[AfterSaleController] getAfterSalesByUserId, userId={}", userId);
        List<AfterSaleResponse> responses = afterSaleService.getAfterSalesByUserId(userId);
        return Result.success(responses);
    }
}
