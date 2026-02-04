package com.yuge.inventory.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 库存预留响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {

    /**
     * 预留ID
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 预留数量
     */
    private Integer qty;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 是否已过期
     */
    private Boolean expired;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
