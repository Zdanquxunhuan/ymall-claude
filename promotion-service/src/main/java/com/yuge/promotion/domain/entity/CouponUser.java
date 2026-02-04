package com.yuge.promotion.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券实体
 */
@Data
@TableName("t_coupon_user")
public class CouponUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户优惠券编号（唯一）
     */
    private String userCouponNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 优惠券ID
     */
    private Long couponId;

    /**
     * 优惠券编码（冗余）
     */
    private String couponCode;

    /**
     * 状态: AVAILABLE/USED/EXPIRED/LOCKED
     */
    private String status;

    /**
     * 领取请求ID（幂等键）
     */
    private String receiveRequestId;

    /**
     * 领取时间
     */
    private LocalDateTime receiveTime;

    /**
     * 生效开始时间
     */
    private LocalDateTime validStartTime;

    /**
     * 生效结束时间
     */
    private LocalDateTime validEndTime;

    /**
     * 使用时间
     */
    private LocalDateTime usedTime;

    /**
     * 使用订单号
     */
    private String usedOrderNo;

    /**
     * 实际优惠金额
     */
    private BigDecimal discountAmount;

    /**
     * 锁定时间（试算锁定）
     */
    private LocalDateTime lockedTime;

    /**
     * 锁定过期时间
     */
    private LocalDateTime lockExpireTime;

    /**
     * 锁定关联的价格锁编号
     */
    private String priceLockNo;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
