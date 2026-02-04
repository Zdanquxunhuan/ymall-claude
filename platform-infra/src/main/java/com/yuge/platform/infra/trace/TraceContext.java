package com.yuge.platform.infra.trace;

import com.alibaba.ttl.TransmittableThreadLocal;
import cn.hutool.core.util.IdUtil;

/**
 * 链路追踪上下文
 * 使用 TransmittableThreadLocal 支持线程池场景下的 traceId 透传
 */
public class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /**
     * 使用 TTL 支持线程池透传
     */
    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();

    /**
     * 获取当前 traceId
     */
    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generateTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * 设置 traceId
     */
    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    /**
     * 清除 traceId
     */
    public static void clear() {
        TRACE_ID.remove();
    }

    /**
     * 生成 traceId
     * 格式: 时间戳(13位) + 随机数(19位) = 32位
     */
    public static String generateTraceId() {
        return IdUtil.getSnowflakeNextIdStr() + IdUtil.fastSimpleUUID().substring(0, 13);
    }
}
