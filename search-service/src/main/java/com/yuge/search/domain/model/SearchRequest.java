package com.yuge.search.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 搜索请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词
     */
    private String keyword;

    /**
     * 分类ID过滤
     */
    private Long categoryId;

    /**
     * 品牌ID过滤
     */
    private Long brandId;

    /**
     * 最低价格
     */
    private java.math.BigDecimal minPrice;

    /**
     * 最高价格
     */
    private java.math.BigDecimal maxPrice;

    /**
     * 排序字段: price, publishTime
     */
    private String sortField;

    /**
     * 排序方向: asc, desc
     */
    private String sortOrder;

    /**
     * 页码（从1开始）
     */
    @Builder.Default
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    @Builder.Default
    private Integer pageSize = 20;

    /**
     * 获取偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
