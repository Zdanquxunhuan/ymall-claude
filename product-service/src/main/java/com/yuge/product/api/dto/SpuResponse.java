package com.yuge.product.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * SPU响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuResponse {

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * SPU标题
     */
    private String title;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
