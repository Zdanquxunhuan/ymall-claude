package com.yuge.cart.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 结算请求
 */
@Data
public class CheckoutRequest {

    /**
     * 要结算的SKU ID列表（可选，为空则结算所有选中商品）
     */
    private List<Long> skuIds;

    /**
     * 使用的优惠券编号列表
     */
    private List<String> userCouponNos;

    /**
     * 锁价时长（分钟），默认15分钟
     */
    private Integer lockMinutes;
}
