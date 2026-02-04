package com.yuge.product.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 创建SKU请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSkuRequest {

    /**
     * 所属SPU ID
     */
    @NotNull(message = "SPU ID不能为空")
    private Long spuId;

    /**
     * SKU标题
     */
    @NotBlank(message = "SKU标题不能为空")
    private String title;

    /**
     * 销售属性JSON，如{"颜色":"黑色","容量":"256GB"}
     */
    private String attrsJson;

    /**
     * 销售价格
     */
    @NotNull(message = "销售价格不能为空")
    @DecimalMin(value = "0.01", message = "销售价格必须大于0")
    private BigDecimal price;

    /**
     * 原价
     */
    private BigDecimal originalPrice;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 条形码
     */
    private String barCode;

    /**
     * 重量n     */
    private BigDecimal weight;
}
