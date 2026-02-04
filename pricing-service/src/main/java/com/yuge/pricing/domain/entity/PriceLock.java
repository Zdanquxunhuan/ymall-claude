package com.yuge.pricing.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 价格锁实体
 */
@Data
@TableName("t_price_lock")
public class PriceLock {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 价格锁编号（唯一）
     */
    private String priceLockNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态: LOCKED/USED/EXPIRED/CANCELED
     */
    private String status;

    /**
     * 商品原价总额
     */
    private BigDecimal originalAmount;

    /**
     * 优惠总额
     */
    private BigDecimal totalDiscount;

    /**
     * 应付金额
     */
    private BigDecimal payableAmount;

    /**
     * 锁价快照（JSON）
     */
    private String snapshotJson;

    /**
     * 分摊明细（JSON）
     */
    private String allocationJson;

    /**
     * 使用的优惠券编号列表（JSON）
     */
    private String couponNosJson;

    /**
     * 签名（防篡改）
     */
    private String signature;

    /**
     * 签名版本
     */
    private Integer signVersion;

    /**
     * 锁定时间
     */
    private LocalDateTime lockedAt;

    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 使用时间
     */
    private LocalDateTime usedAt;

    /**
     * 使用的订单号
     */
    private String usedOrderNo;

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
