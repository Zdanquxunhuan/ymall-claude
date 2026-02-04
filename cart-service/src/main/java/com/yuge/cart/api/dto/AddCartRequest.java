package com.yuge.cart.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 添加购物车请求
 */
@Data
public class AddCartRequest {

    /**
     * SKU ID
     */
    @NotNull(message = "skuId不能为空")
    private Long skuId;

    /**
     * SPU ID
     */
    private Long spuId;

    /**
     * 商品标题
     */
    @NotBlank(message = "商品标题不能为空")
    @Size(max = 256, message = "商品标题长度不能超过256")
    private String title;

    /**
     * 商品图片URL
     */
    private String imageUrl;

    /**
     * 商品单价
     */
    @NotNull(message = "商品单价不能为空")
    @DecimalMin(value = "0.01", message = "商品单价必须大于0")
    private BigDecimal unitPrice;

    /**
     * 购买数量
     */
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    @Max(value = 99, message = "购买数量不能超过99")
    private Integer qty;

    /**
     * SKU规格属性
     */
    private String skuAttrs;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 仓库ID
     */
    private Long warehouseId;
}
