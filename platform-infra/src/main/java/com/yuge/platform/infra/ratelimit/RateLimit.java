package com.yuge.platform.infra.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 支持按接口、用户维度限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流键前缀，默认使用方法签名
     */
    String key() default "";

    /**
     * 每秒允许的请求数 (QPS)
     */
    int qps() default 100;

    /**
     * 令牌桶容量（突发流量容忍度）
     * 默认等于 qps
     */
    int capacity() default -1;

    /**
     * 限流维度
     */
    Dimension dimension() default Dimension.API;

    /**
     * 用户ID获取方式（dimension=USER时生效）
     * HEADER: 从请求头获取
     * PARAM: 从请求参数获取
     * SPEL: SpEL表达式
     */
    UserIdSource userIdSource() default UserIdSource.HEADER;

    /**
     * 用户ID字段名
     */
    String userIdField() default "X-User-Id";

    /**
     * 限流提示消息
     */
    String message() default "请求过于频繁，请稍后重试";

    /**
     * 限流维度枚举
     */
    enum Dimension {
        /**
         * 按接口限流
         */
        API,
        /**
         * 按用户限流
         */
        USER,
        /**
         * 按接口+用户限流
         */
        API_USER
    }

    /**
     * 用户ID来源
     */
    enum UserIdSource {
        HEADER,
        PARAM,
        SPEL
    }
}
