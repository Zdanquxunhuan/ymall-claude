package com.yuge.order.infrastructure.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定价服务客户端接口
 */
public interface PricingClient {

    /**
     * 使用价格锁（下单时调用）
     */
    UsePriceLockResult usePriceLock(String priceLockNo, String orderNo, String signature);

    /**
     * 获取价格锁详情
     */
    PriceLockInfo getPriceLock(String priceLockNo);

    /**
     * 取消价格锁
     */
    void cancelPriceLock(String priceLockNo);

    /**
     * 使用价格锁结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class UsePriceLockResult {
        private Boolean success;
        private String errorMessage;
        private PriceLockInfo priceLockInfo;
    }

    /**
     * 价格锁信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class PriceLockInfo {
        private String priceLockNo;
        private Long userId;
        private String status;
        private BigDecimal originalAmount;
        private BigDecimal totalDiscount;
        private BigDecimal payableAmount;
        private String signature;
        private Integer signVersion;
        private LocalDateTime lockedAt;
        private LocalDateTime expireAt;
        private List<AllocationDetail> allocations;
    }

    /**
     * 分摊明细
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class AllocationDetail {
        private Long skuId;
        private String title;
        private Integer qty;
        private BigDecimal unitPrice;
        private BigDecimal lineOriginalAmount;
        private BigDecimal lineDiscountAmount;
        private BigDecimal linePayableAmount;
    }
}
