package com.yuge.cart.api.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求
 */
@Data
public class RemoveItemsRequest {

    /**
     * SKU ID列表
     */
    @NotEmpty(message = "skuIds不能为空")
    private List<Long> skuIds;
}
