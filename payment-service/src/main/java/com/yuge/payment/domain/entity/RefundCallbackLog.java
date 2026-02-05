package com.yuge.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 退款回调日志实体
 */
@Data
@TableName("t_refund_callback_log")
public class RefundCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 回调唯一ID（用于幂等）
     */
    private String callbackId;

    /**
     * 退款渠道
     */
    private String channel;

    /**
     * 渠道退款流水号
     */
    private String channelRefundNo;

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
     * 签名是否有效
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

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
