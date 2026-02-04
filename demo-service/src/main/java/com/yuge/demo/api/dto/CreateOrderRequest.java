package com.yuge.demo.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /**
     * 客户端请求ID（用于幂等）
     */
    @NotBlank(message = "clientRequestId不能为空")
    private String clientRequestId;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 订单金额
     */
    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额必须大于0")
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;
}
