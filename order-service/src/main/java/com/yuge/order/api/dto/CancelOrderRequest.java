package com.yuge.order.api.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消订单请求
 */
@Data
public class CancelOrderRequest {

    /**
     * 客户端请求ID（幂等键）
     */
    private String clientRequestId;

    /**
     * 取消原因
     */
    @Size(max = 500, message = "取消原因长度不能超过500")
    private String cancelReason;

    /**
     * 操作人
     */
    private String operator;
}
