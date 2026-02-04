package com.yuge.cart.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车商品项
 * 存储在Redis中，Key: cart:{userId} 或 cart:anon:{anonId}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品图片URL
     */
    private String imageUrl;

    /**
     * 商品单价（加入时的价格快照）
     */
    private BigDecimal unitPrice;

    /**
     * 购买数量
     */
    private Integer qty;

    /**
     * 是否选中（用于结算）
     */
    private Boolean checked;

    /**
     * SKU规格属性（如：颜色:红色;尺码:XL）
     */
    private String skuAttrs;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 仓库ID（默认仓库）
     */
    private Long warehouseId;

    /**
     * 加入购物车时间
     */
    private LocalDateTime addedAt;

    /**
     * 最后更新时间
     */
    private LocalDateTime updatedAt;
}
