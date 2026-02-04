package com.yuge.order.application;

import com.yuge.order.api.dto.CancelOrderRequest;
import com.yuge.order.api.dto.CreateOrderRequest;
import com.yuge.order.api.dto.OrderResponse;
import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.entity.OrderItem;
import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.domain.event.OrderCanceledEvent;
import com.yuge.order.domain.event.OrderCreatedEvent;
import com.yuge.order.domain.statemachine.OrderStateMachine;
import com.yuge.order.infrastructure.client.PricingClient;
import com.yuge.order.infrastructure.repository.OrderRepository;
import com.yuge.order.infrastructure.repository.OutboxEventRepository;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.platform.infra.trace.TraceContext;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderStateMachine orderStateMachine;
    private final PricingClient pricingClient;

    /**
     * 创建订单（幂等）
     * 事务内：创建订单 + 写Outbox
     * 
     * 重要：必须传入priceLockNo和signature，防止篡价
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        Long userId = request.getUserId();
        String clientRequestId = request.getClientRequestId();
        String priceLockNo = request.getPriceLockNo();
        String signature = request.getSignature();

        // 1. 幂等检查：根据userId + clientRequestId查询是否已存在
        Optional<Order> existingOrder = orderRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
        if (existingOrder.isPresent()) {
            log.info("[OrderService] Order already exists, userId={}, clientRequestId={}, orderNo={}",
                    userId, clientRequestId, existingOrder.get().getOrderNo());
            return buildOrderResponse(existingOrder.get());
        }

        // 2. 生成订单号
        String orderNo = generateOrderNo();

        // 3. 校验并使用价格锁（防篡价核心逻辑）
        PricingClient.UsePriceLockResult usePriceLockResult = pricingClient.usePriceLock(priceLockNo, orderNo, signature);
        if (!usePriceLockResult.getSuccess()) {
            log.warn("[OrderService] Use price lock failed, priceLockNo={}, orderNo={}, error={}",
                    priceLockNo, orderNo, usePriceLockResult.getErrorMessage());
            throw new BizException(ErrorCode.INVALID_PARAM, "价格锁校验失败: " + usePriceLockResult.getErrorMessage());
        }

        PricingClient.PriceLockInfo priceLockInfo = usePriceLockResult.getPriceLockInfo();
        
        // 4. 校验用户ID是否匹配
        if (!userId.equals(priceLockInfo.getUserId()) && priceLockInfo.getUserId() != 0L) {
            log.warn("[OrderService] User ID mismatch, requestUserId={}, priceLockUserId={}",
                    userId, priceLockInfo.getUserId());
            throw new BizException(ErrorCode.INVALID_PARAM, "价格锁用户不匹配");
        }

        // 5. 校验商品明细是否匹配（防止篡改商品）
        validateOrderItems(request.getItems(), priceLockInfo.getAllocations());

        // 6. 使用锁价金额作为订单金额（防篡价）
        BigDecimal totalAmount = priceLockInfo.getPayableAmount();

        // 7. 创建订单实体
        Order order = new Order();
        order.setId(IdUtil.getSnowflakeNextId());
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAmount(totalAmount);
        order.setStatus(OrderStatus.CREATED.getCode());
        order.setClientRequestId(clientRequestId);
        order.setPriceLockNo(priceLockNo);
        order.setRemark(request.getRemark());

        // 8. 保存订单
        orderRepository.save(order);

        // 9. 创建订单明细（使用锁价快照中的价格）
        List<OrderItem> orderItems = buildOrderItemsFromPriceLock(orderNo, request.getItems(), priceLockInfo.getAllocations());
        orderRepository.saveItems(orderItems);

        // 10. 记录状态流转
        String eventId = outboxEventRepository.saveOrderCreatedEvent(orderNo, buildOrderCreatedEvent(order, orderItems));
        orderRepository.saveStateFlow(orderNo, null, OrderStatus.CREATED, OrderEvent.CREATE, eventId, "system", "订单创建(锁价:" + priceLockNo + ")");

        log.info("[OrderService] Order created successfully, orderNo={}, userId={}, amount={}, priceLockNo={}",
                orderNo, userId, totalAmount, priceLockNo);

        return buildOrderResponse(order, orderItems);
    }

    /**
     * 校验订单商品与锁价快照是否匹配
     */
    private void validateOrderItems(List<CreateOrderRequest.OrderItemRequest> requestItems,
                                    List<PricingClient.AllocationDetail> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "价格锁快照为空");
        }

        Map<Long, PricingClient.AllocationDetail> allocationMap = allocations.stream()
                .collect(Collectors.toMap(PricingClient.AllocationDetail::getSkuId, Function.identity()));

        for (CreateOrderRequest.OrderItemRequest item : requestItems) {
            PricingClient.AllocationDetail allocation = allocationMap.get(item.getSkuId());
            if (allocation == null) {
                throw new BizException(ErrorCode.INVALID_PARAM, "商品不在锁价快照中: " + item.getSkuId());
            }
            if (!item.getQty().equals(allocation.getQty())) {
                throw new BizException(ErrorCode.INVALID_PARAM, 
                        String.format("商品数量不匹配, skuId=%d, 请求数量=%d, 锁价数量=%d",
                                item.getSkuId(), item.getQty(), allocation.getQty()));
            }
        }

        // 检查是否有遗漏的商品
        if (requestItems.size() != allocations.size()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "订单商品数量与锁价快照不匹配");
        }
    }

    /**
     * 使用锁价快照构建订单明细
     */
    private List<OrderItem> buildOrderItemsFromPriceLock(String orderNo,
                                                          List<CreateOrderRequest.OrderItemRequest> requestItems,
                                                          List<PricingClient.AllocationDetail> allocations) {
        Map<Long, PricingClient.AllocationDetail> allocationMap = allocations.stream()
                .collect(Collectors.toMap(PricingClient.AllocationDetail::getSkuId, Function.identity()));

        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest item : requestItems) {
            PricingClient.AllocationDetail allocation = allocationMap.get(item.getSkuId());
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setSkuId(item.getSkuId());
            orderItem.setQty(item.getQty());
            orderItem.setTitleSnapshot(allocation.getTitle() != null ? allocation.getTitle() : item.getTitle());
            // 使用锁价快照中的单价（防篡价）
            orderItem.setPriceSnapshot(allocation.getUnitPrice() != null ? allocation.getUnitPrice() : item.getPrice());
            orderItem.setPromoSnapshotJson(item.getPromoJson());
            // 记录分摊后的实付金额
            orderItem.setPayableAmount(allocation.getLinePayableAmount());
            orderItem.setDiscountAmount(allocation.getLineDiscountAmount());
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    /**
     * 取消订单（幂等）
     * 事务内：CAS更新状态 + 写Outbox
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse cancelOrder(String orderNo, CancelOrderRequest request) {
        // 1. 查询订单
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在: " + orderNo));

        OrderStatus currentStatus = OrderStatus.of(order.getStatus());

        // 2. 幂等检查：如果已经是取消状态，直接返回
        if (currentStatus == OrderStatus.CANCELED) {
            log.info("[OrderService] Order already canceled, orderNo={}", orderNo);
            return buildOrderResponse(order);
        }

        // 3. 状态机校验
        OrderStatus targetStatus = orderStateMachine.transition(currentStatus, OrderEvent.CANCEL);

        // 4. CAS更新状态
        boolean updated = orderRepository.casUpdateStatus(orderNo, currentStatus, targetStatus, order.getVersion());
        if (!updated) {
            // CAS失败，可能是并发修改，重新查询判断
            Order latestOrder = orderRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在: " + orderNo));
            
            if (OrderStatus.of(latestOrder.getStatus()) == OrderStatus.CANCELED) {
                log.info("[OrderService] Order already canceled by another request, orderNo={}", orderNo);
                return buildOrderResponse(latestOrder);
            }
            
            throw new BizException(ErrorCode.DB_OPTIMISTIC_LOCK, "订单状态已变更，请刷新后重试");
        }

        // 5. 写Outbox事件
        String eventId = outboxEventRepository.saveOrderCanceledEvent(orderNo, 
                buildOrderCanceledEvent(order, request.getCancelReason(), request.getOperator()));

        // 6. 记录状态流转
        orderRepository.saveStateFlow(orderNo, currentStatus, targetStatus, OrderEvent.CANCEL, 
                eventId, request.getOperator(), request.getCancelReason());

        // 7. 取消价格锁（如果有）
        if (order.getPriceLockNo() != null) {
            try {
                pricingClient.cancelPriceLock(order.getPriceLockNo());
            } catch (Exception e) {
                log.warn("[OrderService] Cancel price lock failed, priceLockNo={}, error={}",
                        order.getPriceLockNo(), e.getMessage());
            }
        }

        log.info("[OrderService] Order canceled successfully, orderNo={}", orderNo);

        // 8. 重新查询返回最新状态
        Order canceledOrder = orderRepository.findByOrderNo(orderNo).orElse(order);
        canceledOrder.setStatus(targetStatus.getCode());
        return buildOrderResponse(canceledOrder);
    }

    /**
     * 查询订单详情
     */
    public OrderResponse getOrder(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在: " + orderNo));
        
        List<OrderItem> items = orderRepository.findItemsByOrderNo(orderNo);
        return buildOrderResponse(order, items);
    }

    /**
     * 生成订单号
     * 格式: ORD + 年月日时分秒毫秒 + 4位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "ORD" + timestamp + random;
    }

    /**
     * 计算订单总金额
     */
    private BigDecimal calculateTotalAmount(List<CreateOrderRequest.OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 构建订单明细
     */
    private List<OrderItem> buildOrderItems(String orderNo, List<CreateOrderRequest.OrderItemRequest> items) {
        List<OrderItem> orderItems = new ArrayList<>();
        for (CreateOrderRequest.OrderItemRequest item : items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderNo(orderNo);
            orderItem.setSkuId(item.getSkuId());
            orderItem.setQty(item.getQty());
            orderItem.setTitleSnapshot(item.getTitle());
            orderItem.setPriceSnapshot(item.getPrice());
            orderItem.setPromoSnapshotJson(item.getPromoJson());
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    /**
     * 构建订单创建事件
     */
    private OrderCreatedEvent buildOrderCreatedEvent(Order order, List<OrderItem> items) {
        List<OrderCreatedEvent.OrderItemInfo> itemInfos = items.stream()
                .map(item -> OrderCreatedEvent.OrderItemInfo.builder()
                        .skuId(item.getSkuId())
                        .qty(item.getQty())
                        .title(item.getTitleSnapshot())
                        .price(item.getPriceSnapshot())
                        .build())
                .collect(Collectors.toList());

        return OrderCreatedEvent.builder()
                .eventId(null) // 由Outbox生成
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .items(itemInfos)
                .eventTime(LocalDateTime.now())
                .traceId(TraceContext.getTraceId())
                .version("1.0")
                .build();
    }

    /**
     * 构建订单取消事件
     */
    private OrderCanceledEvent buildOrderCanceledEvent(Order order, String cancelReason, String operator) {
        return OrderCanceledEvent.builder()
                .eventId(null)
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .cancelReason(cancelReason)
                .operator(operator)
                .eventTime(LocalDateTime.now())
                .traceId(TraceContext.getTraceId())
                .version("1.0")
                .build();
    }

    /**
     * 构建订单响应
     */
    private OrderResponse buildOrderResponse(Order order) {
        List<OrderItem> items = orderRepository.findItemsByOrderNo(order.getOrderNo());
        return buildOrderResponse(order, items);
    }

    private OrderResponse buildOrderResponse(Order order, List<OrderItem> items) {
        List<OrderResponse.OrderItemResponse> itemResponses = items.stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .skuId(item.getSkuId())
                        .qty(item.getQty())
                        .title(item.getTitleSnapshot())
                        .price(item.getPriceSnapshot())
                        .promoJson(item.getPromoSnapshotJson())
                        .build())
                .collect(Collectors.toList());

        OrderStatus status = OrderStatus.of(order.getStatus());
        
        return OrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .statusDesc(status.getDesc())
                .priceLockNo(order.getPriceLockNo())
                .remark(order.getRemark())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
