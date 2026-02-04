package com.yuge.platform.infra.ratelimit;

import com.yuge.platform.infra.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 限流切面
 * 拦截标记了 @RateLimit 注解的方法，实现限流处理
 */
@Slf4j
@Aspect
@Component
@Order(0) // 限流优先于幂等执行
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. 构建限流键
        String rateLimitKey = buildRateLimitKey(joinPoint, rateLimit);
        
        // 2. 获取限流参数
        int qps = rateLimit.qps();
        int capacity = rateLimit.capacity() > 0 ? rateLimit.capacity() : qps;
        
        // 3. 执行限流检查
        boolean allowed = rateLimiterService.tryAcquire(rateLimitKey, qps, capacity);
        
        if (!allowed) {
            log.warn("[RateLimit] Request rate limited, key={}, qps={}", rateLimitKey, qps);
            throw BizException.rateLimitExceeded(rateLimit.message());
        }
        
        // 4. 执行业务逻辑
        return joinPoint.proceed();
    }

    /**
     * 构建限流键
     */
    private String buildRateLimitKey(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        
        // 基础键：使用注解配置或方法签名
        String baseKey = StringUtils.hasText(rateLimit.key()) 
                ? rateLimit.key() 
                : getMethodSignature(joinPoint);
        
        switch (rateLimit.dimension()) {
            case API:
                // 仅按接口限流
                keyBuilder.append("api:").append(baseKey);
                break;
                
            case USER:
                // 仅按用户限流
                String userId = extractUserId(rateLimit);
                if (StringUtils.hasText(userId)) {
                    keyBuilder.append("user:").append(userId);
                } else {
                    // 无法获取用户ID时，降级为按接口限流
                    keyBuilder.append("api:").append(baseKey);
                }
                break;
                
            case API_USER:
                // 按接口+用户限流
                keyBuilder.append("api_user:").append(baseKey);
                String uid = extractUserId(rateLimit);
                if (StringUtils.hasText(uid)) {
                    keyBuilder.append(":").append(uid);
                }
                break;
        }
        
        return keyBuilder.toString();
    }

    /**
     * 获取方法签名作为限流键
     */
    private String getMethodSignature(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    /**
     * 提取用户ID
     */
    private String extractUserId(RateLimit rateLimit) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        return switch (rateLimit.userIdSource()) {
            case HEADER -> request.getHeader(rateLimit.userIdField());
            case PARAM -> request.getParameter(rateLimit.userIdField());
            case SPEL -> null; // TODO: 实现 SpEL 表达式解析
        };
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
