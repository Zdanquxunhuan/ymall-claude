package com.yuge.search.domain.service;

import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchRequest;
import com.yuge.search.domain.model.SearchResult;

/**
 * 搜索索引服务接口
 * 定义索引的基本操作
 */
public interface SearchIndexService {

    /**
     * 索引商品文档
     * 
     * @param document 商品文档
     * @return 是否索引成功
     */
    boolean indexDocument(ProductDocument document);

    /**
     * 删除商品文档
     * 
     * @param skuId SKU ID
     * @return 是否删除成功
     */
    boolean deleteDocument(Long skuId);

    /**
     * 获取商品文档
     * 
     * @param skuId SKU ID
     * @return 商品文档，不存在返回null
     */
    ProductDocument getDocument(Long skuId);

    /**
     * 搜索商品
     * 
     * @param request 搜索请求
     * @return 搜索结果
     */
    SearchResult search(SearchRequest request);

    /**
     * 检查事件是否已处理（幂等检查）
     * 
     * @param skuId SKU ID
     * @param eventId 事件ID
     * @return 是否已处理
     */
    boolean isEventProcessed(Long skuId, String eventId);

    /**
     * 获取索引中的文档数量
     * 
     * @return 文档数量
     */
    long count();

    /**
     * 清空索引
     */
    void clear();
}
