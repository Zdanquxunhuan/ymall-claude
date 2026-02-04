package com.yuge.promotion.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 促销试算请求
 */
@Data
public class PromotionApplyRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 商品行列表
     */
    @NotEmpty(message = "商品列表不能为空")
    @Valid
    private List<ItemInfo> items;

    /**
     * 指定使用的优惠券编号列表（可选）
     */
    private List<String> userCouponNos;

    @Data
    public static class ItemInfo {
        @NotNull(message = "SKU ID不能为空")
        private Long skuId;

        @NotNull(message = "数量不能为空")
        private Integer qty;

        @NotNull(message = "单价不能为空")
        private BigDecimal unitPrice;

        /**
         * 商品分类ID（用于判断优惠券适用范围）
         */
        private Long categoryId;
    }
}
