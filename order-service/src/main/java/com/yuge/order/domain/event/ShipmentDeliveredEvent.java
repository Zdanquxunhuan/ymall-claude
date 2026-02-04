package com.yuge.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 签收事件（从fulfillment-service消费）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDeliveredEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 发货单号
     */
    private String shipmentNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 运单号
     */
    private String waybillNo;

    /**
     * 签收时间
     */
    private LocalDateTime deliveredAt;

    /**
     * 事件时间
     */
    private LocalDateTime eventTime;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 版本号
     */
    private String version;
}
