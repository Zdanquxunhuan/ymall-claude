package com.yuge.cart.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 购物车响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    /**
     * 购物车商品列表
     */
    private List<CartItemResponse> items;

    /**
     * 商品总数量
     */
    private Integer totalQty;

    /**
     * 选中商品数量
     */
    private Integer checkedQty;

    /**
     * 选中商品总金额
     */
    private BigDecimal checkedAmount;

    /**
     * 购物车商品项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private Long skuId;
        private Long spuId;
        private String title;
        private String imageUrl;
        private BigDecimal unitPrice;
        private Integer qty;
        private Boolean checked;
        private String skuAttrs;
        private Long categoryId;
        private Long warehouseId;
        private LocalDateTime addedAt;
        private LocalDateTime updatedAt;
        
        /**
         * 行小计金额
         */
        private BigDecimal lineAmount;
    }
}
