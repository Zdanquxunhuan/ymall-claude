package com.yuge.product.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品更新事件
 * 当SKU信息更新时触发
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件版本号（用于兼容性处理）
     */
    public static final String VERSION = "1.0";

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
     * 当前状态
     */
    private String status;

    /**
     * 更新的字段列表
     */
    private List<String> updatedFields;

    /**
     * 创建事件
     */
    public static ProductUpdatedEvent create(String eventId, Long skuId, Long spuId,
            String title, String attrsJson, BigDecimal price, Long categoryId,
            Long brandId, String skuCode, String status, List<String> updatedFields) {
        return ProductUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("PRODUCT_UPDATED")
                .version(VERSION)
                .eventTime(LocalDateTime.now())
                .source("product-service")
                .skuId(skuId)
                .spuId(spuId)
                .title(title)
                .attrsJson(attrsJson)
                .price(price)
                .categoryId(categoryId)
                .brandId(brandId)
                .skuCode(skuCode)
                .status(status)
                .updatedFields(updatedFields)
                .build();
    }
}
