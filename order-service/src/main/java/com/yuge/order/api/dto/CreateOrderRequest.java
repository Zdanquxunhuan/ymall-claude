package com.yuge.order.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class CreateOrderRequest {

    /**
     * 客户端请求ID（幂等键）
     */
    @NotBlank(message = "clientRequestId不能为空")
    @Size(max = 64, message = "clientRequestId长度不能超过64")
    private String clientRequestId;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 价格锁编号（必填，防篡价）
     */
    @NotBlank(message = "priceLockNo不能为空，请先调用结算接口获取锁价")
    @Size(max = 64, message = "priceLockNo长度不能超过64")
    private String priceLockNo;

    /**
     * 价格锁签名（必填，防篡改）
     */
    @NotBlank(message = "signature不能为空")
    @Size(max = 128, message = "signature长度不能超过128")
    private String signature;

    /**
     * 订单明细
     */
    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItemRequest> items;

    /**
     * 备注
     */
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;

    @Data
    public static class OrderItemRequest {

        @NotNull(message = "skuId不能为空")
        private Long skuId;
       @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量至少为1")
        @Max(value = 999, message = "购买数量不能超过999")
        private Integer qty;

        @NotBlank(message = "商品标题不能为空")
        @Size(max = 256, message = "商品标题长度不能超过256")
        private String title;

        @NotNull(message = "商品单价不能为空")
        @DecimalMin(value = "0.01", message = "商品单价必须大于0")
        private BigDecimal price;

        /**
         * 促销信息JSON（可选）
         */
        private String promoJson;
    }
}
