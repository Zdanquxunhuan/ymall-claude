package com.yuge.product.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发布SKU请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishSkuRequest {

    /**
     * 备注
     */
    private String remark;
}
