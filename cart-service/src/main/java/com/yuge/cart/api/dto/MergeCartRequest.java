package com.yuge.cart.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并购物车请求
 */
@Data
public class MergeCartRequest {

    /**
     * 游客ID
     */
    @NotBlank(message = "anonId不能为空")
    private String anonId;

    /**
     * 合并策略: QTY_ADD(数量累加，默认), LATEST_WIN(以最新为准)
     */
    private String mergeStrategy;
}
