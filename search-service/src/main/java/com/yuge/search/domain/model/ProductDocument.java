package com.yuge.search.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 搜索文档模型
 * 存储在索引中的商品文档
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SKU ID (主键)
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
     * 商品状态
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 索引更新时间
     */
    private LocalDateTime indexTime;

    /**
     * 事件版本（用于幂等去重）
     */
    private String eventVersion;

    /**
     * 最后处理的事件ID（用于幂等去重）
     */
    private String lastEventId;
}
