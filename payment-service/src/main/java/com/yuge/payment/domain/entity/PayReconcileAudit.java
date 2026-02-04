package com.yuge.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付对账审计实体
 */
@Data
@TableName("t_pay_reconcile_audit")
public class PayReconcileAudit {

    @TableId(type = IdType.AUTO)
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
     * 操作类型: QUERY/CLOSE/NOTIFY
     */
    private String action;

    /**
     * 操作前状态
     */
    private String beforeStatus;

    /**
     * 操作后状态
     */
    private String afterStatus;

    /**
     * 查询结果: SUCCESS/FAILED/NOT_FOUND/PAYING
     */
    private String queryResult;

    /**
     * 备注
     */
    private String remark;

    /**
     * 链路追踪ID
     */
    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
