package com.yuge.order.infrastructure.repository;

import com.yuge.order.domain.entity.Order;
import com.yuge.order.domain.entity.OrderItem;
import com.yuge.order.domain.entity.OrderStateFlow;
import com.yuge.order.domain.enums.OrderEvent;
import com.yuge.order.domain.enums.OrderStatus;
import com.yuge.order.infrastructure.mapper.OrderItemMapper;
import com.yuge.order.infrastructure.mapper.OrderMapper;
import com.yuge.order.infrastructure.mapper.OrderStateFlowMapper;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 订单仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStateFlowMapper orderStateFlowMapper;

    /**
     * 保存订单
     */
    public void save(Order order) {
        orderMapper.insert(order);
        log.info("[OrderRepo] Order saved, orderNo={}, userId={}", order.getOrderNo(), order.getUserId());
    }

    /**
     * 保存订单明细
     */
    public void saveItems(List<OrderItem> items) {
        for (OrderItem item : items) {
            orderItemMapper.insert(item);
        }
        log.info("[OrderRepo] Order items saved, count={}", items.size());
    }

    /**
     * 根据订单号查询订单
     */
    public Optional<Order> findByOrderNo(String orderNo) {
        Order order = orderMapper.selectByOrderNo(orderNo);
        return Optional.ofNullable(order);
    }

    /**
     * 根据用户ID和客户端请求ID查询订单（幂等查询）
     */
    public Optional<Order> findByUserIdAndClientRequestId(Long userId, String clientRequestId) {
        Order order = orderMapper.selectByUserIdAndClientRequestId(userId, clientRequestId);
        return Optional.ofNullable(order);
    }

    /**
     * 根据订单号查询订单明细
     */
    public List<OrderItem> findItemsByOrderNo(String orderNo) {
        return orderItemMapper.selectByOrderNo(orderNo);
    }

    /**
     * CAS更新订单状态
     *
     * @param orderNo    订单号
     * @param fromStatus 原状态
     * @param toStatus   目标状态
     * @param version    当前版本号
     * @return 是否更新成功
     */
    public boolean casUpdateStatus(String orderNo, OrderStatus fromStatus, OrderStatus toStatus, Integer version) {
        int rows = orderMapper.casUpdateStatus(orderNo, fromStatus.getCode(), toStatus.getCode(), version);
        if (rows > 0) {
            log.info("[OrderRepo] CAS update success, orderNo={}, {} -> {}", orderNo, fromStatus, toStatus);
            return true;
        } else {
            log.warn("[OrderRepo] CAS update failed, orderNo={}, {} -> {}, version={}", 
                    orderNo, fromStatus, toStatus, version);
            return false;
        }
    }

    /**
     * CAS更新订单状态（仅基于状态，不检查版本号）
     * 用于事件驱动的状态更新，防止乱序
     *
     * @param orderNo    订单号
     * @param fromStatus 原状态
     * @param toStatus   目标状态
     * @return 是否更新成功
     */
    public boolean casUpdateStatusOnly(String orderNo, OrderStatus fromStatus, OrderStatus toStatus) {
        int rows = orderMapper.casUpdateStatusOnly(orderNo, fromStatus.getCode(), toStatus.getCode());
        if (rows > 0) {
            log.info("[OrderRepo] CAS update (status only) success, orderNo={}, {} -> {}", 
                    orderNo, fromStatus, toStatus);
            return true;
        } else {
            log.warn("[OrderRepo] CAS update (status only) failed, orderNo={}, {} -> {}", 
                    orderNo, fromStatus, toStatus);
            return false;
        }
    }

    /**
     * 保存状态流转记录
     */
    public void saveStateFlow(String orderNo, OrderStatus fromStatus, OrderStatus toStatus, 
                              OrderEvent event, String eventId, String operator, String remark) {
        OrderStateFlow stateFlow = new OrderStateFlow();
        stateFlow.setOrderNo(orderNo);
        stateFlow.setFromStatus(fromStatus != null ? fromStatus.getCode() : null);
        stateFlow.setToStatus(toStatus.getCode());
        stateFlow.setEvent(event.getCode());
        stateFlow.setEventId(eventId != null ? eventId : UUID.randomUUID().toString().replace("-", ""));
        stateFlow.setOperator(operator);
        stateFlow.setTraceId(TraceContext.getTraceId());
        stateFlow.setRemark(remark);
        
        orderStateFlowMapper.insert(stateFlow);
        log.info("[OrderRepo] State flow saved, orderNo={}, {} -> {}, event={}", 
                orderNo, fromStatus, toStatus, event);
    }

    /**
     * 保存被忽略的状态流转记录（乱序消息）
     */
    public void saveIgnoredStateFlow(String orderNo, String currentStatus, OrderEvent event, 
                                      String eventId, String ignoredReason) {
        OrderStateFlow stateFlow = new OrderStateFlow();
        stateFlow.setOrderNo(orderNo);
        stateFlow.setFromStatus(currentStatus);
        stateFlow.setToStatus(currentStatus); // 状态未变
        stateFlow.setEvent(event.getCode());
        stateFlow.setEventId(eventId);
        stateFlow.setOperator("SYSTEM");
        stateFlow.setTraceId(TraceContext.getTraceId());
        stateFlow.setRemark("[IGNORED] " + ignoredReason);
        
        orderStateFlowMapper.insert(stateFlow);
        log.warn("[OrderRepo] Ignored state flow saved, orderNo={}, currentStatus={}, event={}, reason={}", 
                orderNo, currentStatus, event, ignoredReason);
    }

    /**
     * 查询订单状态流转记录
     */
    public List<OrderStateFlow> findStateFlowsByOrderNo(String orderNo) {
        return orderStateFlowMapper.selectByOrderNo(orderNo);
    }
}
