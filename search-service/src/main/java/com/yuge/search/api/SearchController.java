package com.yuge.search.api;

import com.yuge.platform.infra.common.Result;
import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchRequest;
import com.yuge.search.domain.model.SearchResult;
import com.yuge.search.domain.service.SearchIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 搜索 API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchIndexService searchIndexService;

    /**
     * 商品搜索接口
     * 
     * @param q 搜索关键词
     * @param categoryId 分类ID（可选）
     * @param brandId 品牌ID（可选）
     * @param minPrice 最低价格（可选）
     * @param maxPrice 最高价格（可选）
     * @param sort 排序字段: price, publishTime（可选）
     * @param order 排序方向: asc, desc（可选，默认desc）
     * @param pageNum 页码（可选，默认1）
     * @param pageSize 每页大小（可选，默认20）
     * @return 搜索结果
     */
    @GetMapping
    public Result<SearchResult> search(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "brandId", required = false) Long brandId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "order", required = false, defaultValue = "desc") String order,
            @RequestParam(value = "pageNum", required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {

        log.info("[SearchAPI] Search request: q={}, categoryId={}, brandId={}, sort={}, order={}, page={}/{}",
                q, categoryId, brandId, sort, order, pageNum, pageSize);

        // 参数校验
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 20;
        }

        // 构建搜索请求
        SearchRequest request = SearchRequest.builder()
                .keyword(q)
                .categoryId(categoryId)
                .brandId(brandId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .sortField(sort)
                .sortOrder(order)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();

        // 执行搜索
        SearchResult result = searchIndexService.search(request);

        log.info("[SearchAPI] Search completed: total={}, took={}ms", result.getTotal(), result.getTook());

        return Result.success(result);
    }

    /**
     * 获取单个商品文档
     * 
     * @param skuId SKU ID
     * @return 商品文档
     */
    @GetMapping("/{skuId}")
    public Result<ProductDocument> getDocument(@PathVariable Long skuId) {
        log.info("[SearchAPI] Get document: skuId={}", skuId);

        ProductDocument document = searchIndexService.getDocument(skuId);
        
        if (document == null) {
            return Result.fail("B0001", "商品不存在");
        }

        return Result.success(document);
    }

    /**
     * 获取索引统计信息
     * 
     * @return 索引文档数量
     */
    @GetMapping("/stats")
    public Result<IndexStats> getStats() {
        long count = searchIndexService.count();
        return Result.success(new IndexStats(count));
    }

    /**
     * 索引统计信息
     */
    public record IndexStats(long documentCount) {}
}
