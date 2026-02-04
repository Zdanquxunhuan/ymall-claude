package com.yuge.fulfillment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MQ消费日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_fulfillment_mq_consume_log")
public class MqConsumeLog {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 消费者组
     */
    private String consumerGroup;

    /**
     * 主题
     */
    private String topic;

    /**
     * 标签
     */
    private String tags;

    /**
     * 业务键
     */
    private String bizKey;

    /**
     * 状态: PROCESSING-处理中, SUCCESS-成功, FAILED-失败, IGNORED-已忽略
     */
    private String status;

    /**
     * 处理结果消息
     */
    private String resultMsg;

    /**
     * 忽略原因
     */
    private String ignoredReason;

    /**
     * 处理耗时(毫秒)
     */
    private Long costMs;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
