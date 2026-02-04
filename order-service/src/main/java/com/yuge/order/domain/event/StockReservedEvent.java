package com.yuge.order.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预留成功事件
 * 由 inventory-service 发布，order-service 消费
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservedEvent {

    /**
     * 事件ID（全局唯一，用于幂等）
     */
    private String eventId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 预留明细
     */
    private List<ReservedItem> items;

    /**
     * 事件时间（用于乱序检测）
     */
    private LocalDateTime eventTime;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 事件版本
     */
    private String version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservedItem {
        private Long skuId;
        private Long warehouseId;
        private Integer qty;
    }
}
