package com.yuge.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MQ消费日志实体
 * 
 * 用于实现消费幂等，确保同一消息不会被重复消费
 */
@Data
@TableName("t_mq_consume_log")
public class MqConsumeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件ID（与 Outbox 的 event_id 对应）
     */
    private String eventId;

    /**
     * 消费者组
     */
    private String consumerGroup;

    /**
     * 消费状态: PROCESSING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息标签
     */
    private String tag;

    /**
     * 业务键
     */
    private String bizKey;

    /**
     * 消费结果/错误信息
     */
    private String result;

    /**
     * 忽略原因（乱序消息时记录）
     */
    private String ignoredReason;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 消费耗时（毫秒）
     */
    private Long costMs;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
