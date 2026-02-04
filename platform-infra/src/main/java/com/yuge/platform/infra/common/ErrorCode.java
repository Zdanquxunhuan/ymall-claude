package com.yuge.platform.infra.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码枚举
 * 错误码规范：
 * - 00000: 成功
 * - A开头: 用户端错误（参数错误、认证失败等）
 * - B开头: 系统端错误（内部异常、服务不可用等）
 * - C开头: 第三方服务错误（RPC调用失败、MQ发送失败等）
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS("00000", "成功"),

    // ==================== A: 用户端错误 ====================
    // A0001-A0099: 通用用户错误
    USER_ERROR("A0001", "用户端错误"),
    PARAM_ERROR("A0100", "参数校验失败"),
    PARAM_MISSING("A0101", "必填参数为空"),
    PARAM_INVALID("A0102", "参数格式错误"),
    
    // A0200-A0299: 认证授权错误
    AUTH_ERROR("A0200", "认证失败"),
    TOKEN_EXPIRED("A0201", "Token已过期"),
    TOKEN_INVALID("A0202", "Token无效"),
    PERMISSION_DENIED("A0203", "权限不足"),
    
    // A0300-A0399: 业务规则错误
    BIZ_ERROR("A0300", "业务处理失败"),
    RESOURCE_NOT_FOUND("A0301", "资源不存在"),
    RESOURCE_ALREADY_EXISTS("A0302", "资源已存在"),
    STATE_INVALID("A0303", "状态不合法"),
    OPERATION_NOT_ALLOWED("A0304", "操作不允许"),
    
    // A0400-A0499: 幂等相关错误
    IDEMPOTENT_KEY_MISSING("A0400", "幂等键缺失"),
    IDEMPOTENT_REQUEST_PROCESSING("A0401", "请求正在处理中，请勿重复提交"),
    
    // A0500-A0599: 限流相关错误
    RATE_LIMIT_EXCEEDED("A0500", "请求过于频繁，请稍后重试"),
    RATE_LIMIT_USER_EXCEEDED("A0501", "用户请求频率超限"),
    RATE_LIMIT_API_EXCEEDED("A0502", "接口请求频率超限"),

    // ==================== B: 系统端错误 ====================
    SYSTEM_ERROR("B0001", "系统内部错误"),
    SYSTEM_BUSY("B0002", "系统繁忙，请稍后重试"),
    SYSTEM_TIMEOUT("B0003", "系统执行超时"),
    
    // B0100-B0199: 数据库错误
    DB_ERROR("B0100", "数据库错误"),
    DB_CONNECTION_ERROR("B0101", "数据库连接失败"),
    DB_DUPLICATE_KEY("B0102", "数据重复"),
    DB_OPTIMISTIC_LOCK("B0103", "数据已被修改，请刷新后重试"),
    
    // B0200-B0299: 缓存错误
    CACHE_ERROR("B0200", "缓存服务错误"),
    CACHE_CONNECTION_ERROR("B0201", "缓存连接失败"),

    // ==================== C: 第三方服务错误 ====================
    THIRD_PARTY_ERROR("C0001", "第三方服务错误"),
    
    // C0100-C0199: RPC调用错误
    RPC_ERROR("C0100", "远程调用失败"),
    RPC_TIMEOUT("C0101", "远程调用超时"),
    
    // C0200-C0299: MQ错误
    MQ_ERROR("C0200", "消息队列错误"),
    MQ_SEND_FAILED("C0201", "消息发送失败"),
    MQ_CONSUME_FAILED("C0202", "消息消费失败"),
    INVALID_PARAM("C0203","invalid param" );

    /**
     * 错误码
     */
    private final String code;

    /**
     * 错误描述
     */
    private final String message;

    /**
     * 判断是否成功
     */
    public boolean isSuccess() {
        return SUCCESS.code.equals(this.code);
    }

    /**
     * 根据code获取ErrorCode
     */
    public static ErrorCode of(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
