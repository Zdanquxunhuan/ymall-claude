package com.yuge.fulfillment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货单响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDTO {

    /**
     * 发货单号
     */
    private String shipmentNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 运单号
     */
    private String waybillNo;

    /**
     * 承运商
     */
    private String carrier;

    /**
     * 发货时间
     */
    private LocalDateTime shippedAt;

    /**
     * 签收时间
     */
    private LocalDateTime deliveredAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
