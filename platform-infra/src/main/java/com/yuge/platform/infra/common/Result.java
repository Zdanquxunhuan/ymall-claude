package com.yuge.platform.infra.common;

import com.yuge.platform.infra.trace.TraceContext;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回体
 * 所有API响应都使用此结构，包含traceId便于问题追踪
 *
 * @param <T> 数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private String code;

    /**
     * 错误信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 时间戳
     */
    private long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
        this.traceId = TraceContext.getTraceId();
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(ErrorCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 成功响应（带数据和自定义消息）
     */
    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(ErrorCode.SUCCESS.getCode());
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    /**
     * 失败响应（使用错误码枚举）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(errorCode.getMessage());
        return result;
    }

    /**
     * 失败响应（使用错误码枚举 + 自定义消息）
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 失败响应（自定义错误码和消息）
     */
    public static <T> Result<T> fail(String code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return ErrorCode.SUCCESS.getCode().equals(this.code);
    }

    /**
     * 判断是否失败
     */
    public boolean isFail() {
        return !isSuccess();
    }
}
