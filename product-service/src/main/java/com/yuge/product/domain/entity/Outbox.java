package com.yuge.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox实体 (事务性发件箱)
 * 用于实现可靠的事件发布，保证业务操作和事件发布的原子性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_outbox")
public class Outbox {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 事件ID（UUID）
     */
    private String eventId;

    /**
     * 事件类型: PRODUCT_PUBLISHED, PRODUCT_UPDATED
     */
    private String eventType;

    /**
     * 聚合类型: SKU, SPU
     */
    private String aggregateType;

    /**
     * 聚合ID
     */
    private Long aggregateId;

    /**
     * 事件负载（JSON格式）
     */
    private String payload;

    /**
     * 状态: PENDING-待发送, SENT-已发送, FAILED-发送失败
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
     * 错误信息
     */
    private String errorMessage;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
