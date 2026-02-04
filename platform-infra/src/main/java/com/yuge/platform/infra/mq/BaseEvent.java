package com.yuge.platform.infra.mq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息事件基类
 * 所有MQ消息都应继承此类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（用于幂等）
     */
    private String messageId;

    /**
     * 业务键（用于顺序消费、查询）
     */
    private String businessKey;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 事件版本（用于兼容性处理）
     */
    private String version;

    /**
     * 事件发生时间
     */
    private LocalDateTime eventTime;

    /**
     * 事件来源服务
     */
    private String source;

    /**
     * 业务数据（JSON）
     */
    private String payload;
}
