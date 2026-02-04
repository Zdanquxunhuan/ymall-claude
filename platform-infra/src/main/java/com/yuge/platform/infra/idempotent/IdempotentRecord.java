package com.yuge.platform.infra.idempotent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 幂等记录
 * 存储幂等请求的处理状态和结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotentRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 幂等键
     */
    private String key;

    /**
     * 处理状态
     */
    private Status status;

    /**
     * 响应结果（JSON序列化）
     */
    private String result;

    /**
     * 结果类型（用于反序列化）
     */
    private String resultType;

    /**
     * 创建时间戳
     */
    private Long createdAt;

    /**
     * 过期时间戳
     */
    private Long expireAt;

    /**
     * traceId
     */
    private String traceId;

    /**
     * 处理状态枚举
     */
    public enum Status {
        /**
         * 处理中
         */
        PROCESSING,
        /**
         * 处理成功
         */
        SUCCESS,
        /**
         * 处理失败
         */
        FAILED
    }

    /**
     * 是否处理中
     */
    public boolean isProcessing() {
        return Status.PROCESSING.equals(this.status);
    }

    /**
     * 是否处理完成（成功或失败）
     */
    public boolean isCompleted() {
        return Status.SUCCESS.equals(this.status) || Status.FAILED.equals(this.status);
    }
}
