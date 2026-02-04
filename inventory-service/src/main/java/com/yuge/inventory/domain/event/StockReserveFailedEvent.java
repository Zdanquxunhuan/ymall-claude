package com.yuge.inventory.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存预留失败事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReserveFailedEvent {

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 错误原因
     */
    private String errorMessage;

    /**
     * 失败的SKU ID（如果是单个SKU失败）
     */
    private Long failedSkuId;

    /**
     * 请求的预留明细
     */
    private List<RequestedItem> requestedItems;

    /**
     * 事件时间
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
    public static class RequestedItem {
        private Long skuId;
        private Long warehouseId;
        private Integer qty;
    }
}
