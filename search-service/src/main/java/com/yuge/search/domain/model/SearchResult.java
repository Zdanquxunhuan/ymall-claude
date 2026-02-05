package com.yuge.search.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索结果列表
     */
    private List<ProductDocument> items;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 总页数
     */
    private Integer totalPages;

    /**
     * 搜索耗时（毫秒）
     */
    private Long took;

    /**
     * 创建空结果
     */
    public static SearchResult empty(SearchRequest request) {
        return SearchResult.builder()
                .items(List.of())
                .total(0L)
                .pageNum(request.getPageNum())
                .pageSize(request.getPageSize())
                .totalPages(0)
                .took(0L)
                .build();
    }

    /**
     * 创建搜索结果
     */
    public static SearchResult of(List<ProductDocument> items, long total, SearchRequest request, long took) {
        int totalPages = (int) Math.ceil((double) total / request.getPageSize());
        return SearchResult.builder()
                .items(items)
                .total(total)
                .pageNum(request.getPageNum())
                .pageSize(request.getPageSize())
                .totalPages(totalPages)
                .took(took)
                .build();
    }
}
