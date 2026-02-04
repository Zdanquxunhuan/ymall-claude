package com.yuge.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单实体
 */
@Data
@TableName("t_pay_order")
public class PayOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 支付单号
     */
    private String payNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 支付状态: INIT/PAYING/SUCCESS/FAILED/CLOSED
     */
    private String status;

    /**
     * 支付渠道: MOCK/ALIPAY/WECHAT
     */
    private String channel;

    /**
     * 渠道交易号
     */
    private String channelTradeNo;

    /**
     * 支付成功时间
     */
    private LocalDateTime paidAt;

    /**
     * 支付过期时间
     */
    private LocalDateTime expireAt;

    /**
     * 关闭原因
     */
    private String closeReason;

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
