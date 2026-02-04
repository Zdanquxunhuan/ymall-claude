package com.yuge.platform.infra.exception;

import com.yuge.platform.infra.common.ErrorCode;
import lombok.Getter;

/**
 * 业务异常
 * 所有业务逻辑异常都应该抛出此异常或其子类
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误信息
     */
    private final String message;

    /**
     * 附加数据（可选）
     */
    private Object data;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.message = message;
    }

    public BizException(String code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public BizException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
    }

    /**
     * 设置附加数据
     */
    public BizException withData(Object data) {
        this.data = data;
        return this;
    }

    /**
     * 快速创建业务异常
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    public static BizException of(String code, String message) {
        return new BizException(code, message);
    }

    /**
     * 参数错误
     */
    public static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message);
    }

    /**
     * 资源不存在
     */
    public static BizException notFound(String message) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    /**
     * 状态不合法
     */
    public static BizException stateInvalid(String message) {
        return new BizException(ErrorCode.STATE_INVALID, message);
    }

    /**
     * 操作不允许
     */
    public static BizException operationNotAllowed(String message) {
        return new BizException(ErrorCode.OPERATION_NOT_ALLOWED, message);
    }

    /**
     * 限流异常
     */
    public static BizException rateLimitExceeded() {
        return new BizException(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    public static BizException rateLimitExceeded(String message) {
        return new BizException(ErrorCode.RATE_LIMIT_EXCEEDED, message);
    }

    /**
     * 幂等处理中
     */
    public static BizException idempotentProcessing() {
        return new BizException(ErrorCode.IDEMPOTENT_REQUEST_PROCESSING);
    }
}
