package com.yuge.search;

import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchRequest;
import com.yuge.search.domain.model.SearchResult;
import com.yuge.search.infrastructure.index.MemorySearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存索引服务单元测试
 */
@DisplayName("MemorySearchIndexService 单元测试")
class MemorySearchIndexServiceTest {

    private MemorySearchIndexService indexService;

    @BeforeEach
    void setUp() {
        indexService = new MemorySearchIndexService();
    }

    @Test
    @DisplayName("索引文档 - 成功")
    void shouldIndexDocumentSuccessfully() {
        // Arrange
        ProductDocument document = createTestDocument(1001L, "iPhone 15 Pro 256GB 黑色", 8999.00);

        // Act
        boolean result = indexService.indexDocument(document);

        // Assert
        assertTrue(result);
        assertEquals(1, indexService.count());
        
        ProductDocument retrieved = indexService.getDocument(1001L);
        assertNotNull(retrieved);
        assertEquals("iPhone 15 Pro 256GB 黑色", retrieved.getTitle());
    }

    @Test
    @DisplayName("索引文档 - 空文档返回失败")
    void shouldReturnFalseForNullDocument() {
        // Act
        boolean result = indexService.indexDocument(null);

        // Assert
        assertFalse(result);
        assertEquals(0, indexService.count());
    }

    @Test
    @DisplayName("索引文档 - 更新已存在的文档")
    void shouldUpdateExistingDocument() {
        // Arrange
        ProductDocument doc1 = createTestDocument(1001L, "iPhone 15 Pro", 8999.00);
        ProductDocument doc2 = createTestDocument(1001L, "iPhone 15 Pro Max", 9999.00);

        // Act
        indexService.indexDocument(doc1);
        indexService.indexDocument(doc2);

        // Assert
        assertEquals(1, indexService.count());
        ProductDocument retrieved = indexService.getDocument(1001L);
        assertEquals("iPhone 15 Pro Max", retrieved.getTitle());
        assertEquals(new BigDecimal("9999.00"), retrieved.getPrice());
    }

    @Test
    @DisplayName("删除文档 - 成功")
    void shouldDeleteDocumentSuccessfully() {
        // Arrange
        ProductDocument document = createTestDocument(1001L, "iPhone 15 Pro", 8999.00);
        indexService.indexDocument(document);

        // Act
        boolean result = indexService.deleteDocument(1001L);

        // Assert
        assertTrue(result);
        assertEquals(0, indexService.count());
        assertNull(indexService.getDocument(1001L));
    }

    @Test
    @DisplayName("删除文档 - 不存在的文档返回失败")
    void shouldReturnFalseWhenDeletingNonExistentDocument() {
        // Act
        boolean result = indexService.deleteDocument(9999L);

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("关键词搜索 - 中文分词")
    void shouldSearchByChineseKeyword() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "苹果手机 iPhone 15", 8999.00));
        indexService.indexDocument(createTestDocument(1002L, "华为手机 Mate 60", 6999.00));
        indexService.indexDocument(createTestDocument(1003L, "小米平板", 2999.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .keyword("手机")
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(2, result.getTotal());
        assertTrue(result.getItems().stream().anyMatch(d -> d.getSkuId().equals(1001L)));
        assertTrue(result.getItems().stream().anyMatch(d -> d.getSkuId().equals(1002L)));
    }

    @Test
    @DisplayName("关键词搜索 - 英文分词")
    void shouldSearchByEnglishKeyword() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "iPhone 15 Pro", 8999.00));
        indexService.indexDocument(createTestDocument(1002L, "iPhone 14", 6999.00));
        indexService.indexDocument(createTestDocument(1003L, "Samsung Galaxy", 5999.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .keyword("iphone")
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(2, result.getTotal());
    }

    @Test
    @DisplayName("分类过滤")
    void shouldFilterByCategory() {
        // Arrange
        ProductDocument doc1 = createTestDocument(1001L, "iPhone 15", 8999.00);
        doc1.setCategoryId(1L);
        ProductDocument doc2 = createTestDocument(1002L, "MacBook Pro", 12999.00);
        doc2.setCategoryId(2L);
        
        indexService.indexDocument(doc1);
        indexService.indexDocument(doc2);

        // Act
        SearchRequest request = SearchRequest.builder()
                .categoryId(1L)
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(1, result.getTotal());
        assertEquals(1001L, result.getItems().get(0).getSkuId());
    }

    @Test
    @DisplayName("品牌过滤")
    void shouldFilterByBrand() {
        // Arrange
        ProductDocument doc1 = createTestDocument(1001L, "iPhone 15", 8999.00);
        doc1.setBrandId(1L);
        ProductDocument doc2 = createTestDocument(1002L, "Mate 60", 6999.00);
        doc2.setBrandId(2L);
        
        indexService.indexDocument(doc1);
        indexService.indexDocument(doc2);

        // Act
        SearchRequest request = SearchRequest.builder()
                .brandId(2L)
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(1, result.getTotal());
        assertEquals(1002L, result.getItems().get(0).getSkuId());
    }

    @Test
    @DisplayName("价格范围过滤")
    void shouldFilterByPriceRange() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "Product A", 1000.00));
        indexService.indexDocument(createTestDocument(1002L, "Product B", 2000.00));
        indexService.indexDocument(createTestDocument(1003L, "Product C", 3000.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .minPrice(new BigDecimal("1500"))
                .maxPrice(new BigDecimal("2500"))
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(1, result.getTotal());
        assertEquals(1002L, result.getItems().get(0).getSkuId());
    }

    @Test
    @DisplayName("价格排序 - 升序")
    void shouldSortByPriceAsc() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "Product A", 3000.00));
        indexService.indexDocument(createTestDocument(1002L, "Product B", 1000.00));
        indexService.indexDocument(createTestDocument(1003L, "Product C", 2000.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .sortField("price")
                .sortOrder("asc")
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(3, result.getTotal());
        List<ProductDocument> items = result.getItems();
        assertEquals(1002L, items.get(0).getSkuId()); // 1000
        assertEquals(1003L, items.get(1).getSkuId()); // 2000
        assertEquals(1001L, items.get(2).getSkuId()); // 3000
    }

    @Test
    @DisplayName("价格排序 - 降序")
    void shouldSortByPriceDesc() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "Product A", 3000.00));
        indexService.indexDocument(createTestDocument(1002L, "Product B", 1000.00));
        indexService.indexDocument(createTestDocument(1003L, "Product C", 2000.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .sortField("price")
                .sortOrder("desc")
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(3, result.getTotal());
        List<ProductDocument> items = result.getItems();
        assertEquals(1001L, items.get(0).getSkuId()); // 3000
        assertEquals(1003L, items.get(1).getSkuId()); // 2000
        assertEquals(1002L, items.get(2).getSkuId()); // 1000
    }

    @Test
    @DisplayName("分页")
    void shouldPaginateResults() {
        // Arrange
        for (int i = 1; i <= 25; i++) {
            indexService.indexDocument(createTestDocument((long) i, "Product " + i, 1000.00 + i));
        }

        // Act - 第一页
        SearchRequest request1 = SearchRequest.builder()
                .sortField("price")
                .sortOrder("asc")
                .pageNum(1)
                .pageSize(10)
                .build();
        SearchResult result1 = indexService.search(request1);

        // Act - 第二页
        SearchRequest request2 = SearchRequest.builder()
                .sortField("price")
                .sortOrder("asc")
                .pageNum(2)
                .pageSize(10)
                .build();
        SearchResult result2 = indexService.search(request2);

        // Act - 第三页
        SearchRequest request3 = SearchRequest.builder()
                .sortField("price")
                .sortOrder("asc")
                .pageNum(3)
                .pageSize(10)
                .build();
        SearchResult result3 = indexService.search(request3);

        // Assert
        assertEquals(25, result1.getTotal());
        assertEquals(10, result1.getItems().size());
        assertEquals(3, result1.getTotalPages());
        
        assertEquals(10, result2.getItems().size());
        assertEquals(5, result3.getItems().size());
    }

    @Test
    @DisplayName("幂等检查 - 事件已处理")
    void shouldDetectProcessedEvent() {
        // Arrange
        ProductDocument document = createTestDocument(1001L, "iPhone 15", 8999.00);
        document.setLastEventId("evt_001");
        indexService.indexDocument(document);

        // Act & Assert
        assertTrue(indexService.isEventProcessed(1001L, "evt_001"));
        assertFalse(indexService.isEventProcessed(1001L, "evt_002"));
        assertFalse(indexService.isEventProcessed(9999L, "evt_001"));
    }

    @Test
    @DisplayName("幂等检查 - 重复事件不重复写入")
    void shouldNotDuplicateOnSameEvent() {
        // Arrange
        ProductDocument doc1 = createTestDocument(1001L, "iPhone 15", 8999.00);
        doc1.setLastEventId("evt_001");
        
        ProductDocument doc2 = createTestDocument(1001L, "iPhone 15 Updated", 9999.00);
        doc2.setLastEventId("evt_001"); // 相同的 eventId

        // Act
        indexService.indexDocument(doc1);
        
        // 模拟业务层幂等检查
        if (!indexService.isEventProcessed(1001L, doc2.getLastEventId())) {
            indexService.indexDocument(doc2);
        }

        // Assert - 由于 eventId 相同，第二次不应该更新
        ProductDocument retrieved = indexService.getDocument(1001L);
        assertEquals("iPhone 15", retrieved.getTitle()); // 保持原值
        assertEquals(new BigDecimal("8999.00"), retrieved.getPrice());
    }

    @Test
    @DisplayName("清空索引")
    void shouldClearIndex() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "Product 1", 1000.00));
        indexService.indexDocument(createTestDocument(1002L, "Product 2", 2000.00));
        assertEquals(2, indexService.count());

        // Act
        indexService.clear();

        // Assert
        assertEquals(0, indexService.count());
        assertNull(indexService.getDocument(1001L));
        assertNull(indexService.getDocument(1002L));
    }

    @Test
    @DisplayName("组合查询 - 关键词 + 分类 + 价格范围")
    void shouldHandleComplexQuery() {
        // Arrange
        ProductDocument doc1 = createTestDocument(1001L, "苹果手机 iPhone 15", 8999.00);
        doc1.setCategoryId(1L);
        
        ProductDocument doc2 = createTestDocument(1002L, "苹果手机 iPhone 14", 6999.00);
        doc2.setCategoryId(1L);
        
        ProductDocument doc3 = createTestDocument(1003L, "苹果电脑 MacBook", 12999.00);
        doc3.setCategoryId(2L);
        
        ProductDocument doc4 = createTestDocument(1004L, "华为手机 Mate 60", 5999.00);
        doc4.setCategoryId(1L);
        
        indexService.indexDocument(doc1);
        indexService.indexDocument(doc2);
        indexService.indexDocument(doc3);
        indexService.indexDocument(doc4);

        // Act - 搜索"苹果" + 分类1 + 价格6000-9000
        SearchRequest request = SearchRequest.builder()
                .keyword("苹果")
                .categoryId(1L)
                .minPrice(new BigDecimal("6000"))
                .maxPrice(new BigDecimal("9000"))
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(2, result.getTotal());
        assertTrue(result.getItems().stream().anyMatch(d -> d.getSkuId().equals(1001L)));
        assertTrue(result.getItems().stream().anyMatch(d -> d.getSkuId().equals(1002L)));
    }

    @Test
    @DisplayName("空关键词搜索 - 返回所有文档")
    void shouldReturnAllDocumentsWhenNoKeyword() {
        // Arrange
        indexService.indexDocument(createTestDocument(1001L, "Product 1", 1000.00));
        indexService.indexDocument(createTestDocument(1002L, "Product 2", 2000.00));
        indexService.indexDocument(createTestDocument(1003L, "Product 3", 3000.00));

        // Act
        SearchRequest request = SearchRequest.builder()
                .pageNum(1)
                .pageSize(20)
                .build();
        SearchResult result = indexService.search(request);

        // Assert
        assertEquals(3, result.getTotal());
    }

    // ==================== 辅助方法 ====================

    private ProductDocument createTestDocument(Long skuId, String title, double price) {
        return ProductDocument.builder()
                .skuId(skuId)
                .spuId(skuId / 10)
                .title(title)
                .price(new BigDecimal(String.valueOf(price)))
                .categoryId(1L)
                .brandId(1L)
                .skuCode("SKU" + skuId)
                .status("PUBLISHED")
                .publishTime(LocalDateTime.now())
                .indexTime(LocalDateTime.now())
                .build();
    }
}
