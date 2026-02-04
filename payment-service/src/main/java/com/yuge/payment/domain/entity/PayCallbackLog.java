package com.yuge.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调日志实体
 */
@Data
@TableName("t_pay_callback_log")
public class PayCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付单号
     */
    private String payNo;

    /**
     * 回调唯一ID（用于幂等）
     */
    private String callbackId;

    /**
     * 支付渠道
     */
    private String channel;

    /**
     * 渠道交易号
     */
    private String channelTradeNo;

    /**
     * 回调状态: SUCCESS/FAILED
     */
    private String callbackStatus;

    /**
     * 原始回调报文
     */
    private String rawPayload;

    /**
     * 签名
     */
    private String signature;

    /**
     * 签名是否有效: 0-无效 1-有效
     */
    private Integer signatureValid;

    /**
     * 处理结果: PROCESSED/IGNORED/FAILED
     */
    private String processResult;

    /**
     * 处理结果说明
     */
    private String processMessage;

    /**
     * 链路追踪ID
     */
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
