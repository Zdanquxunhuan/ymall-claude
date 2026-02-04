package com.yuge.cart.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuge.cart.api.dto.*;
import com.yuge.cart.domain.entity.CartItem;
import com.yuge.cart.domain.entity.CartMergeLog;
import com.yuge.cart.domain.enums.MergeStrategy;
import com.yuge.cart.infrastructure.client.InventoryClient;
import com.yuge.cart.infrastructure.client.PricingClient;
import com.yuge.cart.infrastructure.redis.CartRedisService;
import com.yuge.cart.infrastructure.repository.CartMergeLogMapper;
import com.yuge.platform.infra.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 购物车服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRedisService cartRedisService;

    @Mock
    private CartMergeLogMapper cartMergeLogMapper;

    @Mock
    private PricingClient pricingClient;

    @Mock
    private InventoryClient inventoryClient;

    private CartService cartService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        cartService = new CartService(cartRedisService, cartMergeLogMapper, 
                pricingClient, inventoryClient, objectMapper);
    }

    @Test
    @DisplayName("添加商品到购物车-登录用户")
    void addItem_loggedInUser_shouldAddSuccessfully() {
        // Arrange
        Long userId = 1001L;
        AddCartRequest request = new AddCartRequest();
        request.setSkuId(10001L);
        request.setTitle("测试商品");
        request.setUnitPrice(BigDecimal.valueOf(99.00));
        request.setQty(2);

        CartItem addedItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        when(cartRedisService.addItem(anyString(), any(CartItem.class))).thenReturn(addedItem);
        when(cartRedisService.getAll(anyString())).thenReturn(Collections.singletonList(addedItem));

        // Act
        CartResponse response = cartService.addItem(userId, null, request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(2, response.getTotalQty());

        verify(cartRedisService).addItem(eq("1001"), any(CartItem.class));
    }

    @Test
    @DisplayName("添加商品到购物车-游客用户")
    void addItem_anonymousUser_shouldAddSuccessfully() {
        // Arrange
        String anonId = "anon-uuid-123";
        AddCartRequest request = new AddCartRequest();
        request.setSkuId(10001L);
        request.setTitle("测试商品");
        request.setUnitPrice(BigDecimal.valueOf(99.00));
        request.setQty(2);

        CartItem addedItem = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        when(cartRedisService.addItem(anyString(), any(CartItem.class))).thenReturn(addedItem);
        when(cartRedisService.getAll(anyString())).thenReturn(Collections.singletonList(addedItem));

        // Act
        CartResponse response = cartService.addItem(null, anonId, request);

        // Assert
        assertNotNull(response);
        verify(cartRedisService).addItem(eq("anon:anon-uuid-123"), any(CartItem.class));
    }

    @Test
    @DisplayName("合并购物车-数量累加策略")
    void mergeCart_qtyAddStrategy_shouldAccumulateQty() {
        // Arrange
        Long userId = 1001L;
        MergeCartRequest request = new MergeCartRequest();
        request.setAnonId("anon-uuid-123");
        request.setMergeStrategy("QTY_ADD");

        // 用户购物车有商品A(数量3)
        CartItem userItem = createCartItem(10001L, "商品A", BigDecimal.valueOf(99.00), 3);
        userItem.setUpdatedAt(LocalDateTime.now().minusHours(1));

        // 游客购物车有商品A(数量2)和商品B(数量1)
        CartItem anonItem1 = createCartItem(10001L, "商品A", BigDecimal.valueOf(99.00), 2);
        anonItem1.setUpdatedAt(LocalDateTime.now());
        CartItem anonItem2 = createCartItem(10002L, "商品B", BigDecimal.valueOf(199.00), 1);
        anonItem2.setUpdatedAt(LocalDateTime.now());

        when(cartRedisService.getAll("1001")).thenReturn(new ArrayList<>(Collections.singletonList(userItem)));
        when(cartRedisService.getAll("anon:anon-uuid-123")).thenReturn(Arrays.asList(anonItem1, anonItem2));
        when(cartMergeLogMapper.insert(any(CartMergeLog.class))).thenReturn(1);

        // Act
        CartResponse response = cartService.mergeCart(userId, request);

        // Assert
        assertNotNull(response);

        // 验证合并日志
        ArgumentCaptor<CartMergeLog> logCaptor = ArgumentCaptor.forClass(CartMergeLog.class);
        verify(cartMergeLogMapper).insert(logCaptor.capture());
        CartMergeLog mergeLog = logCaptor.getValue();
        assertEquals(userId, mergeLog.getUserId());
        assertEquals("anon-uuid-123", mergeLog.getAnonId());
        assertEquals("QTY_ADD", mergeLog.getMergeStrategy());
        assertEquals(2, mergeLog.getMergedSkuCount());
        assertEquals(1, mergeLog.getConflictSkuCount());

        // 验证清空游客车
        verify(cartRedisService).clear("anon:anon-uuid-123");
    }

    @Test
    @DisplayName("合并购物车-游客车为空")
    void mergeCart_emptyAnonCart_shouldReturnUserCart() {
        // Arrange
        Long userId = 1001L;
        MergeCartRequest request = new MergeCartRequest();
        request.setAnonId("anon-uuid-123");

        CartItem userItem = createCartItem(10001L, "商品A", BigDecimal.valueOf(99.00), 3);
        userItem.setUpdatedAt(LocalDateTime.now());

        when(cartRedisService.getAll("1001")).thenReturn(Collections.singletonList(userItem));
        when(cartRedisService.getAll("anon:anon-uuid-123")).thenReturn(Collections.emptyList());

        // Act
        CartResponse response = cartService.mergeCart(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());

        // 不应该记录合并日志
        verify(cartMergeLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("合并购物车-用户未登录应抛异常")
    void mergeCart_notLoggedIn_shouldThrowException() {
        // Arrange
        MergeCartRequest request = new MergeCartRequest();
        request.setAnonId("anon-uuid-123");

        // Act & Assert
        assertThrows(BizException.class, () -> cartService.mergeCart(null, request));
    }

    @Test
    @DisplayName("结算-成功锁价")
    void checkout_successfulLock_shouldReturnCanOrder() {
        // Arrange
        Long userId = 1001L;
        CheckoutRequest request = new CheckoutRequest();
        request.setLockMinutes(15);

        CartItem item = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        item.setChecked(true);
        item.setWarehouseId(1L);

        when(cartRedisService.getAll(anyString())).thenReturn(Collections.singletonList(item));

        // 库存充足
        InventoryClient.StockInfo stockInfo = InventoryClient.StockInfo.builder()
                .skuId(10001L)
                .warehouseId(1L)
                .availableQty(100)
                .requestQty(2)
                .sufficient(true)
                .build();
        when(inventoryClient.queryAvailableStock(anyList())).thenReturn(Collections.singletonList(stockInfo));

        // 锁价成功
        PricingClient.LockResult lockResult = PricingClient.LockResult.builder()
                .success(true)
                .priceLockNo("PL202401011000001234")
                .signature("sha256...")
                .signVersion(1)
                .originalAmount(BigDecimal.valueOf(198.00))
                .totalDiscount(BigDecimal.valueOf(20.00))
                .payableAmount(BigDecimal.valueOf(178.00))
                .expireAt(LocalDateTime.now().plusMinutes(15))
                .allocations(Collections.singletonList(
                        PricingClient.LockResult.AllocationDetail.builder()
                                .skuId(10001L)
                                .title("测试商品")
                                .qty(2)
                                .unitPrice(BigDecimal.valueOf(99.00))
                                .lineOriginalAmount(BigDecimal.valueOf(198.00))
                                .lineDiscountAmount(BigDecimal.valueOf(20.00))
                                .linePayableAmount(BigDecimal.valueOf(178.00))
                                .build()
                ))
                .build();
        when(pricingClient.lock(any())).thenReturn(lockResult);

        // Act
        CheckoutResponse response = cartService.checkout(userId, null, request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getCanOrder());
        assertEquals("PL202401011000001234", response.getPriceLockNo());
        assertEquals("sha256...", response.getSignature());
        assertEquals(BigDecimal.valueOf(178.00), response.getPayableAmount());
    }

    @Test
    @DisplayName("结算-库存不足")
    void checkout_insufficientStock_shouldReturnCannotOrder() {
        // Arrange
        Long userId = 1001L;
        CheckoutRequest request = new CheckoutRequest();

        CartItem item = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 10);
        item.setChecked(true);
        item.setWarehouseId(1L);

        when(cartRedisService.getAll(anyString())).thenReturn(Collections.singletonList(item));

        // 库存不足
        InventoryClient.StockInfo stockInfo = InventoryClient.StockInfo.builder()
                .skuId(10001L)
                .warehouseId(1L)
                .availableQty(5)
                .requestQty(10)
                .sufficient(false)
                .build();
        when(inventoryClient.queryAvailableStock(anyList())).thenReturn(Collections.singletonList(stockInfo));

        // Act
        CheckoutResponse response = cartService.checkout(userId, null, request);

        // Assert
        assertNotNull(response);
        assertFalse(response.getCanOrder());
        assertEquals("部分商品库存不足", response.getFailReason());
    }

    @Test
    @DisplayName("结算-没有选中商品应抛异常")
    void checkout_noCheckedItems_shouldThrowException() {
        // Arrange
        Long userId = 1001L;
        CheckoutRequest request = new CheckoutRequest();

        CartItem item = createCartItem(10001L, "测试商品", BigDecimal.valueOf(99.00), 2);
        item.setChecked(false); // 未选中

        when(cartRedisService.getAll(anyString())).thenReturn(Collections.singletonList(item));

        // Act & Assert
        assertThrows(BizException.class, () -> cartService.checkout(userId, null, request));
    }

    @Test
    @DisplayName("获取购物车-计算选中金额")
    void getCart_shouldCalculateCheckedAmount() {
        // Arrange
        Long userId = 1001L;

        CartItem item1 = createCartItem(10001L, "商品A", BigDecimal.valueOf(100.00), 2);
        item1.setChecked(true);
        item1.setUpdatedAt(LocalDateTime.now());

        CartItem item2 = createCartItem(10002L, "商品B", BigDecimal.valueOf(50.00), 3);
        item2.setChecked(false);
        item2.setUpdatedAt(LocalDateTime.now());

        when(cartRedisService.getAll(anyString())).thenReturn(Arrays.asList(item1, item2));

        // Act
        CartResponse response = cartService.getCart(userId, null);

        // Assert
        assertNotNull(response);
        assertEquals(2, response.getItems().size());
        assertEquals(5, response.getTotalQty()); // 2 + 3
        assertEquals(2, response.getCheckedQty()); // 只有item1选中
        assertEquals(BigDecimal.valueOf(200.00), response.getCheckedAmount()); // 100 * 2
    }

    @Test
    @DisplayName("清空购物车")
    void clear_shouldCallRedisService() {
        // Arrange
        Long userId = 1001L;

        // Act
        cartService.clear(userId, null);

        // Assert
        verify(cartRedisService).clear("1001");
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
                .categoryId(100L)
                .build();
    }
}
