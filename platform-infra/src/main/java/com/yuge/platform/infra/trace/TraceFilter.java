package com.yuge.platform.infra.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 过滤器
 * 1. 从请求头获取或生成 traceId
 * 2. 设置到 MDC 供日志使用
 * 3. 响应头回传 traceId
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // 1. 从请求头获取 traceId，没有则生成
            String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
            if (!StringUtils.hasText(traceId)) {
                traceId = TraceContext.generateTraceId();
            }

            // 2. 设置到上下文和 MDC
            TraceContext.setTraceId(traceId);
            MDC.put(TraceContext.TRACE_ID_MDC_KEY, traceId);

            // 3. 响应头回传 traceId
            response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);

            // 4. 继续执行
            filterChain.doFilter(request, response);
        } finally {
            // 5. 清理上下文
            TraceContext.clear();
            MDC.remove(TraceContext.TRACE_ID_MDC_KEY);
        }
    }
}
