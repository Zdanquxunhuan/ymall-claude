package com.yuge.product.application;

import com.yuge.platform.infra.exception.BizException;
import com.yuge.product.api.dto.CreateSkuRequest;
import com.yuge.product.api.dto.CreateSpuRequest;
import com.yuge.product.api.dto.SkuResponse;
import com.yuge.product.api.dto.SpuResponse;
import com.yuge.product.domain.enums.SkuStatus;
import com.yuge.product.domain.enums.SpuStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProductService 单元测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    void shouldCreateSpuSuccessfully() {
        // Arrange
        CreateSpuRequest request = CreateSpuRequest.builder()
                .title("iPhone 15")
                .categoryId(1001L)
                .brandId(1L)
                .description("Apple iPhone 15 智能手机")
                .build();

        // Act
        SpuResponse response = productService.createSpu(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getSpuId());
        assertEquals("iPhone 15", response.getTitle());
        assertEquals(1001L, response.getCategoryId());
        assertEquals(1L, response.getBrandId());
        assertEquals(SpuStatus.DRAFT.getCode(), response.getStatus());
        assertEquals("草稿", response.getStatusDesc());
    }

    @Test
    void shouldCreateSkuSuccessfully() {
        // Arrange - 先创建SPU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("iPhone 15")
                .categoryId(1001L)
                .brandId(1L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("iPhone 15 黑色 256GB")
                .attrsJson("{\"颜色\":\"黑色\",\"容量\":\"256GB\"}")
                .price(new BigDecimal("6999.00"))
                .originalPrice(new BigDecimal("7499.00"))
                .skuCode("IPHONE15-BLK-256")
                .build();

        // Act
        SkuResponse response = productService.createSku(skuRequest);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getSkuId());
        assertEquals(spuResponse.getSpuId(), response.getSpuId());
        assertEquals("iPhone 15 黑色 256GB", response.getTitle());
        assertEquals(new BigDecimal("6999.00"), response.getPrice());
        assertEquals(SkuStatus.DRAFT.getCode(), response.getStatus());
        assertEquals("草稿", response.getStatusDesc());
    }

    @Test
    void shouldThrowExceptionWhenCreateSkuWithInvalidSpuId() {
        // Arrange
        CreateSkuRequest request = CreateSkuRequest.builder()
                .spuId(99999L) // 不存在的SPU ID
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .build();

        // Act & Assert
        BizException exception = assertThrows(BizException.class, () -> {
            productService.createSku(request);
        });
        assertTrue(exception.getMessage().contains("SPU不存在"));
    }

    @Test
    void shouldPublishSkuSuccessfully() {
        // Arrange - 创建SPU和SKU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("MacBook Pro")
                .categoryId(1002L)
                .brandId(1L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("MacBook Pro 14 M3")
                .price(new BigDecimal("12999.00"))
                .build();
        SkuResponse skuResponse = productService.createSku(skuRequest);

        // Act
        SkuResponse publishedSku = productService.publishSku(skuResponse.getSkuId());

        // Assert
        assertNotNull(publishedSku);
        assertEquals(SkuStatus.PUBLISHED.getCode(), publishedSku.getStatus());
        assertEquals("已发布", publishedSku.getStatusDesc());
        assertNotNull(publishedSku.getPublishTime());
    }

    @Test
    void shouldThrowExceptionWhenPublishAlreadyPublishedSku() {
        // Arrange - 创建并发布SKU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .build();
        SkuResponse skuResponse = productService.createSku(skuRequest);
        productService.publishSku(skuResponse.getSkuId());

        // Act & Assert - 再次发布应该失败
        BizException exception = assertThrows(BizException.class, () -> {
            productService.publishSku(skuResponse.getSkuId());
        });
        assertTrue(exception.getMessage().contains("当前状态不允许发布"));
    }

    @Test
    void shouldOfflineSkuSuccessfully() {
        // Arrange - 创建并发布SKU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .build();
        SkuResponse skuResponse = productService.createSku(skuRequest);
        productService.publishSku(skuResponse.getSkuId());

        // Act
        SkuResponse offlinedSku = productService.offlineSku(skuResponse.getSkuId());

        // Assert
        assertNotNull(offlinedSku);
        assertEquals(SkuStatus.OFFLINE.getCode(), offlinedSku.getStatus());
        assertEquals("已下架", offlinedSku.getStatusDesc());
    }

    @Test
    void shouldThrowExceptionWhenOfflineDraftSku() {
        // Arrange - 创建SKU（草稿状态）
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .build();
        SkuResponse skuResponse = productService.createSku(skuRequest);

        // Act & Assert - 草稿状态不能下架
        BizException exception = assertThrows(BizException.class, () -> {
            productService.offlineSku(skuResponse.getSkuId());
        });
        assertTrue(exception.getMessage().contains("当前状态不允许下架"));
    }

    @Test
    void shouldGetSkuByIdSuccessfully() {
        // Arrange
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .brandId(1L)
       .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .skuCode("TEST-SKU-001")
                .build();
        SkuResponse createdSku = productService.createSku(skuRequest);

        // Act
        SkuResponse response = productService.getSkuById(createdSku.getSkuId());

        // Assert
        assertNotNull(response);
        assertEquals(createdSku.getSkuId(), response.getSkuId());
        assertEquals("Test SKU", response.getTitle());
        assertEquals("TEST-SKU-001", response.getSkuCode());
        assertNotNull(response.getSpu());
        assertEquals("Test SPU", response.getSpu().getTitle());
    }

    @Test
    void shouldThrowExceptionWhenGetNonExistentSku() {
        // Act & Assert
        BizException exception = assertThrows(BizException.class, () -> {
            productService.getSkuById(99999L);
        });
        assertTrue(exception.getMessage().contains("SKU不存在"));
    }

    @Test
    void shouldUpdateSkuSuccessfully() {
        // Arrange
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Original Title")
                .price(new BigDecimal("100.00"))
                .build();
        SkuResponse createdSku = productService.createSku(skuRequest);

        CreateSkuRequest updateRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Updated Title")
                .price(new BigDecimal("150.00"))
                .build();

        // Act
        SkuResponse updatedSku = productService.updateSku(createdSku.getSkuId(), updateRequest);

        // Assert
        assertNotNull(updatedSku);
        assertEquals("Updated Title", updatedSku.getTitle());
        assertEquals(new BigDecimal("150.00"), updatedSku.getPrice());
    }

    @Test
    void shouldRepublishOfflinedSku() {
        // Arrange - 创建、发布、下架SKU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("Test SPU")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        CreateSkuRequest skuRequest = CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("Test SKU")
                .price(new BigDecimal("100.00"))
                .build();
        SkuResponse skuResponse = productService.createSku(skuRequest);
        productService.publishSku(skuResponse.getSkuId());
        productService.offlineSku(skuResponse.getSkuId());

        // Act - 重新发布
        SkuResponse republishedSku = productService.publishSku(skuResponse.getSkuId());

        // Assert
        assertNotNull(republishedSku);
        assertEquals(SkuStatus.PUBLISHED.getCode(), republishedSku.getStatus());
    }

    @Test
    void shouldGetSkusBySpuId() {
        // Arrange - 创建SPU和多个SKU
        CreateSpuRequest spuRequest = CreateSpuRequest.builder()
                .title("iPhone 15")
                .categoryId(1001L)
                .build();
        SpuResponse spuResponse = productService.createSpu(spuRequest);

        productService.createSku(CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("iPhone 15 黑色 128GB")
                .price(new BigDecimal("5999.00"))
                .build());

        productService.createSku(CreateSkuRequest.builder()
                .spuId(spuResponse.getSpuId())
                .title("iPhone 15 白色 256GB")
                .price(new BigDecimal("6999.00"))
                .build());

        // Act
        var skuList = productService.getSkusBySpuId(spuResponse.getSpuId());

        // Assert
        assertNotNull(skuList);
        assertEquals(2, skuList.size());
    }
}
