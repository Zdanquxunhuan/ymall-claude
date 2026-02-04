package com.yuge.cart.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * 更新购物车数量请求
 */
@Data
public class UpdateQtyRequest {

    /**
     * SKU ID
     */
    @NotNull(message = "skuId不能为空")
    private Long skuId;

    /**
     * 新数量
     */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 99, message = "数量不能超过99")
    private Integer qty;
}
