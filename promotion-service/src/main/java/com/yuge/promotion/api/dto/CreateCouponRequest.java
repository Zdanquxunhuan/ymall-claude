package com.yuge.promotion.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券请求
 */
@Data
public class CreateCouponRequest {

    /**
     * 优惠券名称
     */
    @NotBlank(message = "优惠券名称不能为空")
    private String name;

    /**
     * 优惠券类型: FULL_REDUCTION/DISCOUNT/FIXED_AMOUNT
     */
    @NotBlank(message = "优惠券类型不能为空")
    private String type;

    /**
     * 使用门槛金额（满X元可用）
     */
    @NotNull(message = "门槛金额不能为空")
    private BigDecimal thresholdAmount;

    /**
     * 优惠金额（满减券/固定金额券）
     */
    private BigDecimal discountAmount;

    /**
     * 折扣比例（折扣券，如0.8表示8折）
     */
    private BigDecimal discountRate;

    /**
     * 最大优惠金额（折扣券封顶）
     */
    private BigDecimal maxDiscountAmount;

    /**
     * 发行总量
     */
    @NotNull(message = "发行总量不能为空")
    @Positive(message = "发行总量必须大于0")
    private Integer totalQuantity;

    /**
     * 每人限领数量
     */
    @NotNull(message = "每人限领数量不能为空")
    @Positive(message = "每人限领数量必须大于0")
    private Integer perUserLimit;

    /**
     * 适用商品范围: ALL/CATEGORY/SKU
     */
    private String applicableScope = "ALL";

    /**
     * 适用商品ID列表（JSON数组）
     */
    private String applicableItems;

    /**
     * 生效开始时间
     */
    @NotNull(message = "生效开始时间不能为空")
    private LocalDateTime validStartTime;

    /**
     * 生效结束时间
     */
    @NotNull(message = "生效结束时间不能为空")
    private LocalDateTime validEndTime;

    /**
     * 领取后有效天数（与validEndTime二选一）
     */
    private Integer validDays;

    /**
     * 备注
     */
    private String remark;
}
