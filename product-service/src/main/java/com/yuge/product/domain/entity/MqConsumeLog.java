package com.yuge.product.domain.entity;

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
 * 用于消费幂等
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_mq_consume_log")
public class MqConsumeLog {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 事件ID（消息唯一标识）
     */
    private String eventId;

    /**
     * 消费者组
     */
    private String consumerGroup;

    /**
     * 消息主题
     */
    private String topic;

    /**
     * 消息标签
     */
    private String tags;

    /**
     * 业务键
     */
    private String bizKey;

    /**
     * 消费状态: PROCESSING-处理中, SUCCESS-成功, FAILED-失败
     */
    private String status;

    /**
     * 消费结果/错误信息
     */
    private String result;

    /**
     * 消费耗时(毫秒)
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
