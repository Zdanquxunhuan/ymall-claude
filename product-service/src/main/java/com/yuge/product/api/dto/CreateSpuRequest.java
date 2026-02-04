package com.yuge.product.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建SPU请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpuRequest {

    /**
     * SPU标题
     */
    @NotBlank(message = "SPU标题不能为空")
    private String title;

    /**
     * 分类ID
     */
    @NotNull(message = "分类ID不能为空")
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 商品描述
     */
    private String description;
}
