package com.yuge.order.application;

import com.yuge.order.api.dto.CreateOrderRequest;
import com.yuge.order.api.dto.OrderResponse;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.entity.OrderItem;
import com.yuge.order.domain.statemachine.OrderStateMachine;
import com.yuge.order.infrastructure.client.PricingClient;
import com.yuge.order.infrastructure.repository.OrderRepository;
import com.yuge.order.infrastructure.repository.OutboxEventRepository;
import com.yuge.platform.infra.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 订单服务单元测试 - 锁价校验
 */
@ExtendWith(MockitoExtension.class)
class OrderServicePriceLockTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OrderStateMachine orderStateMachine;

    @Mock
    private PricingClient pricingClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, outboxEventRepository, 
                orderStateMachine, pricingClient);
    }

    @Test
    @DisplayName("创建订单-锁价校验成功")
    void createOrder_validPriceLock_shouldCreateSuccessfully() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        // 幂等检查-订单不存在
        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // 锁价校验成功
        PricingClient.UsePriceLockResult usePriceLockResult = PricingClient.UsePriceLockResult.builder()
                .success(true)
                .priceLockInfo(PricingClient.PriceLockInfo.builder()
                        .priceLockNo("PL202401011000001234")
                        .userId(1001L)
                        .status("USED")
                        .originalAmount(BigDecimal.valueOf(198.00))
                        .totalDiscount(BigDecimal.valueOf(20.00))
                        .payableAmount(BigDecimal.valueOf(178.00))
                        .allocations(Collections.singletonList(
                                PricingClient.AllocationDetail.builder()
                                        .skuId(10001L)
                                        .title("测试商品")
                                        .qty(2)
                                        .unitPrice(BigDecimal.valueOf(99.00))
                                        .lineOriginalAmount(BigDecimal.valueOf(198.00))
                                        .lineDiscountAmount(BigDecimal.valueOf(20.00))
                                        .linePayableAmount(BigDecimal.valueOf(178.00))
                                        .build()
                        ))
                        .build())
                .build();
        when(pricingClient.usePriceLock(anyString(), anyString(), anyString()))
                .thenReturn(usePriceLockResult);

        when(outboxEventRepository.saveOrderCreatedEvent(anyString(), any()))
                .thenReturn("event-001");

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(178.00), response.getAmount()); // 使用锁价金额
        assertEquals("PL202401011000001234", response.getPriceLockNo());

        verify(orderRepository).save(any(Order.class));
        verify(orderRepository).saveItems(anyList());
    }

    @Test
    @DisplayName("创建订单-锁价签名验证失败")
    void createOrder_invalidSignature_shouldThrowException() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // 锁价校验失败-签名错误
        PricingClient.UsePriceLockResult usePriceLockResult = PricingClient.UsePriceLockResult.builder()
                .success(false)
                .errorMessage("签名验证失败")
                .build();
        when(pricingClient.usePriceLock(anyString(), anyString(), anyString()))
                .thenReturn(usePriceLockResult);

        // Act & Assert
        BizException exception = assertThrows(BizException.class, 
                () -> orderService.createOrder(request));
        assertTrue(exception.getMessage().contains("签名验证失败"));
    }

    @Test
    @DisplayName("创建订单-锁价已过期")
    void createOrder_expiredPriceLock_shouldThrowException() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // 锁价校验失败-已过期
        PricingClient.UsePriceLockResult usePriceLockResult = PricingClient.UsePriceLockResult.builder()
                .success(false)
                .errorMessage("价格锁已过期")
                .build();
        when(pricingClient.usePriceLock(anyString(), anyString(), anyString()))
                .thenReturn(usePriceLockResult);

        // Act & Assert
        BizException exception = assertThrows(BizException.class, 
                () -> orderService.createOrder(request));
        assertTrue(exception.getMessage().contains("价格锁已过期"));
    }

    @Test
    @DisplayName("创建订单-用户ID不匹配")
    void createOrder_userIdMismatch_shouldThrowException() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // 锁价校验成功但用户ID不匹配
        PricingClient.UsePriceLockResult usePriceLockResult = PricingClient.UsePriceLockResult.builder()
                .success(true)
                .priceLockInfo(PricingClient.PriceLockInfo.builder()
                        .priceLockNo("PL202401011000001234")
                        .userId(9999L) // 不同的用户ID
                        .status("USED")
                        .payableAmount(BigDecimal.valueOf(178.00))
                        .allocations(Collections.singletonList(
                                PricingClient.AllocationDetail.builder()
                                        .skuId(10001L)
                                        .qty(2)
                                        .unitPrice(BigDecimal.valueOf(99.00))
                                        .build()
                        ))
                        .build())
                .build();
        when(pricingClient.usePriceLock(anyString(), anyString(), anyString()))
                .thenReturn(usePriceLockResult);

        // Act & Assert
        BizException exception = assertThrows(BizException.class, 
                () -> orderService.createOrder(request));
        assertTrue(exception.getMessage().contains("用户不匹配"));
    }

    @Test
    @DisplayName("创建订单-商品数量不匹配")
    void createOrder_qtyMismatch_shouldThrowException() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        // 锁价校验成功但商品数量不匹配
        PricingClient.UsePriceLockResult usePriceLockResult = PricingClient.UsePriceLockResult.builder()
                .success(true)
                .priceLockInfo(PricingClient.PriceLockInfo.builder()
                        .priceLockNo("PL202401011000001234")
                        .userId(1001L)
                        .status("USED")
                        .payableAmount(BigDecimal.valueOf(178.00))
                        .allocations(Collections.singletonList(
                                PricingClient.AllocationDetail.builder()
                                        .skuId(10001L)
                                        .qty(5) // 锁价时数量是5，但请求是2
                                        .unitPrice(BigDecimal.valueOf(99.00))
                                        .build()
                        ))
                        .build())
                .build();
        when(pricingClient.usePriceLock(anyString(), anyString(), anyString()))
                .thenReturn(usePriceLockResult);

        // Act & Assert
        BizException exception = assertThrows(BizException.class, 
                () -> orderService.createOrder(request));
        assertTrue(exception.getMessage().contains("数量不匹配"));
    }

    @Test
    @DisplayName("创建订单-幂等返回已存在订单")
    void createOrder_idempotent_shouldReturnExistingOrder() {
        // Arrange
        CreateOrderRequest request = createOrderRequest();

        Order existingOrder = new Order();
        existingOrder.setId(1L);
        existingOrder.setOrderNo("ORD202401011000001234");
        existingOrder.setUserId(1001L);
        existingOrder.setAmount(BigDecimal.valueOf(178.00));
        existingOrder.setStatus("CREATED");
        existingOrder.setPriceLockNo("PL202401011000001234");

        when(orderRepository.findByUserIdAndClientRequestId(anyLong(), anyString()))
                .thenReturn(Optional.of(existingOrder));
        when(orderRepository.findItemsByOrderNo(anyString()))
                .thenReturn(Collections.emptyList());

        // Act
        OrderResponse response = orderService.createOrder(request);

        // Assert
        assertNotNull(response);
        assertEquals("ORD202401011000001234", response.getOrderNo());

        // 不应该调用锁价
        verify(pricingClient, never()).usePriceLock(anyString(), anyString(), anyString());
    }

    private CreateOrderRequest createOrderRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setClientRequestId("req-uuid-123");
        request.setUserId(1001L);
        request.setPriceLockNo("PL202401011000001234");
        request.setSignature("sha256...");

        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setSkuId(10001L);
        item.setQty(2);
        item.setTitle("测试商品");
        item.setPrice(BigDecimal.valueOf(99.00));

        request.setItems(Collections.singletonList(item));
        return request;
    }
}
