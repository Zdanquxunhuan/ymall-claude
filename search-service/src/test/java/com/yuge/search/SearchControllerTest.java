package com.yuge.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.search.domain.model.ProductDocument;
import com.yuge.search.domain.model.SearchResult;
import com.yuge.search.domain.service.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 搜索控制器集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SearchController 集成测试")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 清空索引
        searchIndexService.clear();
        
        // 准备测试数据
        searchIndexService.indexDocument(createDocument(1001L, "苹果手机 iPhone 15 Pro 256GB", 
                new BigDecimal("8999.00"), 1L, 1L));
        searchIndexService.indexDocument(createDocument(1002L, "苹果手机 iPhone 14 128GB", 
                new BigDecimal("5999.00"), 1L, 1L));
        searchIndexService.indexDocument(createDocument(1003L, "华为手机 Mate 60 Pro", 
                new BigDecimal("6999.00"), 1L, 2L));
        searchIndexService.indexDocument(createDocument(1004L, "小米平板 Pad 6", 
                new BigDecimal("2499.00"), 2L, 3L));
        searchIndexService.indexDocument(createDocument(1005L, "苹果电脑 MacBook Pro 14", 
                new BigDecimal("14999.00"), 3L, 1L));
    }

    @Test
    @DisplayName("搜索接口 - 关键词搜索")
    void shouldSearchByKeyword() throws Exception {
        MvcResult result = mockMvc.perform(get("/search")
                        .param("q", "手机")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(3))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("iPhone"));
        assertTrue(content.contains("Mate"));
    }

    @Test
    @DisplayName("搜索接口 - 分类过滤")
    void shouldFilterByCategory() throws Exception {
        mockMvc.perform(get("/search")
                        .param("categoryId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    @DisplayName("搜索接口 - 品牌过滤")
    void shouldFilterByBrand() throws Exception {
        mockMvc.perform(get("/search")
                        .param("brandId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(3)); // 苹果品牌3个商品
    }

    @Test
    @DisplayName("搜索接口 - 价格范围过滤")
    void shouldFilterByPriceRange() throws Exception {
        mockMvc.perform(get("/search")
                        .param("minPrice", "5000")
                        .param("maxPrice", "8000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(2)); // 5999, 6999
    }

    @Test
    @DisplayName("搜索接口 - 价格升序排序")
    void shouldSortByPriceAsc() throws Exception {
        MvcResult result = mockMvc.perform(get("/search")
                        .param("sort", "price")
                        .param("order", "asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(5))
                .andReturn();

        // 验证第一个是最便宜的
        String content = result.getResponse().getContentAsString();
        assertTrue(content.contains("小米平板")); // 2499 最便宜
    }

    @Test
    @DisplayName("搜索接口 - 分页")
    void shouldPaginate() throws Exception {
        // 第一页
        mockMvc.perform(get("/search")
                        .param("pageNum", "1")
                        .param("pageSize", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(3));

        // 第二页
        mockMvc.perform(get("/search")
                        .param("pageNum", "2")
                        .param("pageSize", "2")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageNum").value(2));
    }

    @Test
    @DisplayName("搜索接口 - 组合查询")
    void shouldHandleComplexQuery() throws Exception {
        // 搜索"苹果" + 分类1(手机) + 价格5000-9000
        mockMvc.perform(get("/search")
                        .param("q", "苹果")
                        .param("categoryId", "1")
                        .param("minPrice", "5000")
                        .param("maxPrice", "9000")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(2)); // iPhone 15 Pro 和 iPhone 14
    }

    @Test
    @DisplayName("获取单个商品")
    void shouldGetDocumentById() throws Exception {
        mockMvc.perform(get("/search/1001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.skuId").value(1001))
                .andExpect(jsonPath("$.data.title").value("苹果手机 iPhone 15 Pro 256GB"));
    }

    @Test
    @DisplayName("获取不存在的商品")
    void shouldReturnErrorForNonExistentDocument() throws Exception {
        mockMvc.perform(get("/search/9999")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("B0001"))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    @DisplayName("获取索引统计")
    void shouldGetStats() throws Exception {
        mockMvc.perform(get("/search/stats")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.documentCount").value(5));
    }

    @Test
    @DisplayName("空关键词搜索 - 返回所有商品")
    void shouldReturnAllWhenNoKeyword() throws Exception {
        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    // ==================== 辅助方法 ====================

    private ProductDocument createDocument(Long skuId, String title, BigDecimal price, 
                                           Long categoryId, Long brandId) {
        return ProductDocument.builder()
                .skuId(skuId)
                .spuId(skuId / 10)
                .title(title)
                .price(price)
                .categoryId(categoryId)
                .brandId(brandId)
                .skuCode("SKU" + skuId)
                .status("PUBLISHED")
                .publishTime(LocalDateTime.now())
                .indexTime(LocalDateTime.now())
                .lastEventId("evt_" + skuId)
                .build();
    }
}
