package com.yuge.promotion.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板实体
 */
@Data
@TableName("t_coupon")
public class Coupon {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠券编码（唯一）
     */
    private String couponCode;

    /**
     * 优惠券名称
     */
    private String name;

    /**
     * 优惠券类型: FULL_REDUCTION/DISCOUNT/FIXED_AMOUNT
     */
    private String type;

    /**
     * 优惠券状态: DRAFT/ACTIVE/PAUSED/EXPIRED/EXHAUSTED
     */
    private String status;

    /**
     * 使用门槛金额（满X元可用）
     */
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
    private Integer totalQuantity;

    /**
     * 已发放数量
     */
    private Integer issuedQuantity;

    /**
     * 每人限领数量
     */
    private Integer perUserLimit;

    /**
     * 适用商品范围: ALL/CATEGORY/SKU
     */
    private String applicableScope;

    /**
     * 适用商品ID列表（JSON数组）
     */
    private String applicableItems;

    /**
     * 生效开始时间
     */
    private LocalDateTime validStartTime;

    /**
     * 生效结束时间
     */
    private LocalDateTime validEndTime;

    /**
     * 领取后有效天数（与validEndTime二选一）
     */
    private Integer validDays;

    /**
     * 备注
     */
    private String remark;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
