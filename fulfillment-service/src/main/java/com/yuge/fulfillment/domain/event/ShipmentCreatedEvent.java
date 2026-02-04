package com.yuge.fulfillment.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货单创建事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentCreatedEvent {

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
