package com.yuge.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款单实体
 */
@Data
@TableName("t_refund_order")
public class RefundOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 原支付单号
     */
    private String payNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 退款金额
     */
    private BigDecimal amount;

    /**
     * 退款状态: INIT/REFUNDING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 退款渠道
     */
    private String channel;

    /**
     * 渠道退款流水号
     */
    private String channelRefundNo;

    /**
     * 退款原因
     */
    private String refundReason;

    /**
     * 退款成功时间
     */
    private LocalDateTime refundedAt;

    /**
     * 退款明细JSON
     */
    private String itemsJson;

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
