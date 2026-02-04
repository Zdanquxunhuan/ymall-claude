package com.yuge.cart.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuge.cart.domain.entity.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 购物车Redis服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CartRedisServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private CartRedisService cartRedisService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        cartRedisService = new CartRedisService(redisTemplate, objectMapper);
        ReflectionTestUtils.setField(cartRedisService, "expireDays", 30);
        ReflectionTestUtils.setField(cartRedisService, "maxItems", 100);
        ReflectionTestUtils.setField(cartRedisService, "maxQtyPerSku", 99);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    @DisplayName("添加新商品到购物车")
    void addItem_newItem_shouldAddSuccessfully() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem item = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);

        when(hashOperations.size(anyString())).thenReturn(0L);
        when(hashOperations.get(anyString(), anyString())).thenReturn(null);

        // Act
        CartItem result = cartRedisService.addItem(cartKey, item);

        // Assert
        assertNotNull(result);
        assertEquals(10001L, result.getSkuId());
        assertEquals(2, result.getQty());
        assertTrue(result.getChecked());
        assertNotNull(result.getAddedAt());

        verify(hashOperations).put(eq("cart:1001"), eq("10001"), anyString());
        verify(redisTemplate).expire(eq("cart:1001"), any());
    }

    @Test
    @DisplayName("添加已存在商品-数量累加")
    void addItem_existingItem_shouldAccumulateQty() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem existingItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 3);
        existingItem.setAddedAt(LocalDateTime.now().minusHours(1));
        existingItem.setUpdatedAt(LocalDateTime.now().minusHours(1));

        CartItem newItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);

        when(hashOperations.size(anyString())).thenReturn(1L);
        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(objectMapper.writeValueAsString(existingItem));

        // Act
        CartItem result = cartRedisService.addItem(cartKey, newItem);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getQty()); // 3 + 2 = 5

        verify(hashOperations).put(eq("cart:1001"), eq("10001"), anyString());
    }

    @Test
    @DisplayName("添加商品-数量超过上限应截断")
    void addItem_exceedMaxQty_shouldTruncate() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem existingItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 95);
        existingItem.setAddedAt(LocalDateTime.now().minusHours(1));
        existingItem.setUpdatedAt(LocalDateTime.now().minusHours(1));

        CartItem newItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 10);

        when(hashOperations.size(anyString())).thenReturn(1L);
        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(objectMapper.writeValueAsString(existingItem));

        // Act
        CartItem result = cartRedisService.addItem(cartKey, newItem);

        // Assert
        assertEquals(99, result.getQty()); // 上限99
    }

    @Test
    @DisplayName("更新商品数量")
    void updateQty_shouldUpdateSuccessfully() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem existingItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        existingItem.setAddedAt(LocalDateTime.now().minusHours(1));
        existingItem.setUpdatedAt(LocalDateTime.now().minusHours(1));

        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(objectMapper.writeValueAsString(existingItem));

        // Act
        CartItem result = cartRedisService.updateQty(cartKey, 10001L, 5);

        // Assert
        assertEquals(5, result.getQty());
        verify(hashOperations).put(eq("cart:1001"), eq("10001"), anyString());
    }

    @Test
    @DisplayName("更新选中状态")
    void checkItem_shouldUpdateCheckedStatus() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem existingItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        existingItem.setChecked(true);
        existingItem.setAddedAt(LocalDateTime.now().minusHours(1));
        existingItem.setUpdatedAt(LocalDateTime.now().minusHours(1));

        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(objectMapper.writeValueAsString(existingItem));

        // Act
        CartItem result = cartRedisService.checkItem(cartKey, 10001L, false);

        // Assert
        assertFalse(result.getChecked());
    }

    @Test
    @DisplayName("移除商品")
    void removeItem_shouldDeleteFromRedis() {
        // Arrange
        String cartKey = "1001";
        when(hashOperations.delete(anyString(), any())).thenReturn(1L);

        // Act
        cartRedisService.removeItem(cartKey, 10001L);

        // Assert
        verify(hashOperations).delete("cart:1001", "10001");
    }

    @Test
    @DisplayName("清空购物车")
    void clear_shouldDeleteKey() {
        // Arrange
        String cartKey = "1001";

        // Act
        cartRedisService.clear(cartKey);

        // Assert
        verify(redisTemplate).delete("cart:1001");
    }

    @Test
    @DisplayName("获取所有商品")
    void getAll_shouldReturnAllItems() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem item1 = createCartItem(10001L, "商品1", BigDecimal.valueOf(99.00), 2);
        item1.setUpdatedAt(LocalDateTime.now().minusMinutes(10));
        CartItem item2 = createCartItem(10002L, "商品2", BigDecimal.valueOf(199.00), 1);
        item2.setUpdatedAt(LocalDateTime.now());

        Map<Object, Object> entries = new HashMap<>();
        entries.put("10001", objectMapper.writeValueAsString(item1));
        entries.put("10002", objectMapper.writeValueAsString(item2));

        when(hashOperations.entries(anyString())).thenReturn(entries);

        // Act
        List<CartItem> result = cartRedisService.getAll(cartKey);

        // Assert
        assertEquals(2, result.size());
        // 按更新时间倒序，item2应该在前面
        assertEquals(10002L, result.get(0).getSkuId());
        assertEquals(10001L, result.get(1).getSkuId());
    }

    @Test
    @DisplayName("获取选中的商品")
    void getCheckedItems_shouldReturnOnlyCheckedItems() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem item1 = createCartItem(10001L, "商品1", BigDecimal.valueOf(99.00), 2);
        item1.setChecked(true);
        item1.setUpdatedAt(LocalDateTime.now());
        CartItem item2 = createCartItem(10002L, "商品2", BigDecimal.valueOf(199.00), 1);
        item2.setChecked(false);
        item2.setUpdatedAt(LocalDateTime.now());

        Map<Object, Object> entries = new HashMap<>();
        entries.put("10001", objectMapper.writeValueAsString(item1));
        entries.put("10002", objectMapper.writeValueAsString(item2));

        when(hashOperations.entries(anyString())).thenReturn(entries);

        // Act
        List<CartItem> result = cartRedisService.getCheckedItems(cartKey);

        // Assert
        assertEquals(1, result.size());
        assertEquals(10001L, result.get(0).getSkuId());
    }

    @Test
    @DisplayName("获取单个商品")
    void getItem_existingItem_shouldReturnItem() throws Exception {
        // Arrange
        String cartKey = "1001";
        CartItem item = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);

        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(objectMapper.writeValueAsString(item));

        // Act
        Optional<CartItem> result = cartRedisService.getItem(cartKey, 10001L);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(10001L, result.get().getSkuId());
    }

    @Test
    @DisplayName("获取不存在的商品")
    void getItem_nonExistingItem_shouldReturnEmpty() {
        // Arrange
        String cartKey = "1001";
        when(hashOperations.get(anyString(), eq("10001"))).thenReturn(null);

        // Act
        Optional<CartItem> result = cartRedisService.getItem(cartKey, 10001L);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("游客购物车Key构建")
    void anonCartKey_shouldBuildCorrectly() {
        String result = CartRedisService.anonCartKey("uuid-123");
        assertEquals("anon:uuid-123", result);
    }

    @Test
    @DisplayName("用户购物车Key构建")
    void userCartKey_shouldBuildCorrectly() {
        String result = CartRedisService.userCartKey(1001L);
        assertEquals("1001", result);
    }

    private CartItem createCartItem(Long skuId, String title, BigDecimal unitPrice, int qty) {
        return CartItem.builder()
                .skuId(skuId)
                .spuId(skuId / 10)
                .title(title)
                .unitPrice(unitPrice)
                .qty(qty)
                .checked(true)
                .warehouseId(1L)
                .build();
    }
}
