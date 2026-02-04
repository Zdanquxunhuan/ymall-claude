package com.yuge.platform.infra.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 幂等注解
 * 标记在Controller方法上，实现接口幂等性
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等键来源
     * HEADER: 从请求头获取 (默认 X-Idempotency-Key)
     * BODY: 从请求体获取 (字段名由 keyField 指定)
     * PARAM: 从请求参数获取
     * SPEL: 使用 SpEL 表达式
     */
    KeySource keySource() default KeySource.HEADER;

    /**
     * 幂等键字段名
     * - keySource=HEADER 时，表示请求头名称，默认 X-Idempotency-Key
     * - keySource=BODY 时，表示请求体中的字段名，如 clientRequestId
     * - keySource=PARAM 时，表示请求参数名
     * - keySource=SPEL 时，表示 SpEL 表达式
     */
    String keyField() default "X-Idempotency-Key";

    /**
     * 幂等键前缀，用于区分不同业务
     */
    String prefix() default "idempotent";

    /**
     * 幂等有效期，默认24小时
     */
    long timeout() default 24;

    /**
     * 时间单位，默认小时
     */
    TimeUnit timeUnit() default TimeUnit.HOURS;

    /**
     * 是否存储响应结果
     * true: 存储结果，重复请求返回相同结果
     * false: 仅做去重，重复请求返回处理中提示
     */
    boolean storeResult() default true;

    /**
     * 幂等键来源枚举
     */
    enum KeySource {
        HEADER,
        BODY,
        PARAM,
        SPEL
    }
}
