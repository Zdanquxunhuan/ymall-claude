package com.yuge.demo.application;

import com.yuge.demo.api.dto.CreateOrderRequest;
import com.yuge.demo.api.dto.OrderResponse;
import com.yuge.demo.domain.entity.DemoOrder;
import com.yuge.demo.domain.enums.OrderStatus;
import com.yuge.demo.infrastructure.mapper.DemoOrderMapper;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.platform.infra.common.ErrorCode;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Demo业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoService {

    private final DemoOrderMapper demoOrderMapper;

    /**
     * 创建订单
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 生成订单号
        String orderNo = generateOrderNo();
        
        // 2. 构建订单实体
        DemoOrder order = new DemoOrder();
        order.setId(IdUtil.getSnowflakeNextId());
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.CREATED.name());
        order.setRemark(request.getRemark());
        
        // 3. 保存订单
        demoOrderMapper.insert(order);
        log.info("Order created successfully, orderNo={}, userId={}", orderNo, request.getUserId());
        
        // 4. 模拟业务处理耗时
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 5. 返回响应
        return toResponse(order);
    }

    /**
     * 根据订单号查询订单
     */
    public OrderResponse getOrderByNo(String orderNo) {
        DemoOrder order = demoOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在: " + orderNo);
        }
        return toResponse(order);
    }

    /**
     * 生成订单号
     * 格式: ORD + 年月日时分秒 + 6位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.valueOf((int) ((Math.random() * 900000) + 100000));
        return "ORD" + timestamp + random;
    }

    /**
     * 转换为响应对象
     */
    private OrderResponse toResponse(DemoOrder order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .amount(order.getAmount())
                .status(order.getStatus())
                .remark(order.getRemark())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
