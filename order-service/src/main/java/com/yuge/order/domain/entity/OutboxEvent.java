package com.yuge.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.yuge.order.domain.enums.OutboxStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Outbox事件发件箱实体
 * 
 * 用于实现 Transactional Outbox 模式，确保业务操作和消息发送的最终一致性
 */
@Data
@TableName("t_outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 事件ID（全局唯一，用于幂等和追踪）
     */
    private String eventId;

    /**
     * 业务键(如订单号)
     */
    private String bizKey;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息标签
     */
    private String tag;

    /**
     * 消息内容(JSON)
     */
    private String payloadJson;

    /**
     * 状态: NEW/RETRY/SENT/DEAD
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryAt;

    /**
     * 发送成功时间
     */
    private LocalDateTime sentAt;

    /**
     * 最后一次错误信息
     */
    private String lastError;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 乐观锁版本号（用于并发控制）
     */
    @Version
    private Integer version;

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

    /**
     * 获取状态枚举
     */
    public OutboxStatus getStatusEnum() {
        return OutboxStatus.of(this.status);
    }

    /**
     * 是否可以被处理
     */
    public boolean isProcessable() {
        return getStatusEnum().isProcessable();
    }

    /**
     * 是否超过最大重试次数
     */
    public boolean isExhausted() {
        return this.retryCount != null && this.maxRetry != null 
                && this.retryCount >= this.maxRetry;
    }
}
