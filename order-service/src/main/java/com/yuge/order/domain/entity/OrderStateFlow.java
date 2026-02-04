package com.yuge.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单状态流转审计实体
 */
@Data
@TableName("t_order_state_flow")
public class OrderStateFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 原状态
     */
    private String fromStatus;

    /**
     * 目标状态
     */
    private String toStatus;

    /**
     * 触发事件
     */
    private String event;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
