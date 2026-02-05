package com.yuge.search.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品发布事件（本地副本）
 * 与 product-service 的 ProductPublishedEvent 保持一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPublishedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件版本
     */
    private String version;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 事件来源
     */
    private String source;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * SPU ID
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
     * 分类ID
     */
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
}
