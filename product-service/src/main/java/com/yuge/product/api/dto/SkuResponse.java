package com.yuge.product.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkuResponse {

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 所属SPU ID
     */
    private Long spuId;

    /**
     * SKU标题
     */
    private String title;

    /**
     * 销售属性JSON
     */
    private String attrsJson;

    /**
     * 销售价格
     */
    private BigDecimal price;

    /**
     * 原价
     */
    private BigDecimal originalPrice;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 条形码
     */
    private String barCode;

    /**
     * 重量(kg)
     */
    private BigDecimal weight;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * SPU信息（可选）
     */
    private SpuResponse spu;
}
