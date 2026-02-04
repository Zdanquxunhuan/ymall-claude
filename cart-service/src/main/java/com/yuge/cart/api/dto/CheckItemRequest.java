package com.yuge.cart.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新选中状态请求
 */
@Data
public class CheckItemRequest {

    /**
     * SKU ID
     */
    @NotNull(message = "skuId不能为空")
    private Long skuId;

    /**
     * 是否选中
     */
    @NotNull(message = "checked不能为空")
    private Boolean checked;
}
