package com.yuge.platform.infra.idempotent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.common.Result;
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
import java.util.Optional;

/**
 * 幂等切面
 * 拦截标记了 @Idempotent 注解的方法，实现幂等处理
 */
@Slf4j
@Aspect
@Component
@Order(1)
@RequiredArgsConstructor
public class IdempotentAspect {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 获取幂等键
        String idempotentKey = extractIdempotentKey(joinPoint, idempotent);
        if (!StringUtils.hasText(idempotentKey)) {
            throw new BizException(ErrorCode.IDEMPOTENT_KEY_MISSING, "幂等键不能为空");
        }

        // 2. 构建完整的幂等键（前缀 + 业务键）
        String fullKey = idempotent.prefix() + ":" + idempotentKey;
        log.info("[Idempotent] Processing request, key={}", fullKey);

        // 3. 尝试获取幂等锁
        Optional<IdempotentRecord> existingRecord = idempotencyService.tryAcquire(
                fullKey, idempotent.timeout(), idempotent.timeUnit());

        // 4. 如果已存在记录
        if (existingRecord.isPresent()) {
            IdempotentRecord record = existingRecord.get();
            
            // 4.1 如果正在处理中
            if (record.isProcessing()) {
                log.warn("[Idempotent] Request is processing, key={}, originalTraceId={}", 
                        fullKey, record.getTraceId());
                throw BizException.idempotentProcessing();
            }
            
            // 4.2 如果已处理完成且需要返回结果
            if (record.isCompleted() && idempotent.storeResult() && record.getResult() != null) {
                log.info("[Idempotent] Returning cached result, key={}, status={}", fullKey, record.getStatus());
                return parseStoredResult(record, joinPoint);
            }
            
            // 4.3 已处理但不存储结果，返回成功
            return Result.success(null, "请求已处理");
        }

        // 5. 执行业务逻辑
        Object result = null;
        try {
            result = joinPoint.proceed();
            
            // 6. 标记成功并存储结果
            if (idempotent.storeResult()) {
                idempotencyService.markSuccess(fullKey, result);
            } else {
                idempotencyService.markSuccess(fullKey, null);
            }
            
            return result;
        } catch (BizException e) {
            // 业务异常，标记失败但不释放锁（防止重复提交）
            idempotencyService.markFailed(fullKey);
            throw e;
        } catch (Exception e) {
            // 系统异常，释放锁允许重试
            idempotencyService.release(fullKey);
            throw e;
        }
    }

    /**
     * 提取幂等键
     */
    private String extractIdempotentKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }

        return switch (idempotent.keySource()) {
            case HEADER -> request.getHeader(idempotent.keyField());
            case PARAM -> request.getParameter(idempotent.keyField());
            case BODY -> extractFromBody(joinPoint, idempotent.keyField());
            case SPEL -> evaluateSpel(joinPoint, idempotent.keyField());
        };
    }

    /**
     * 从请求体中提取幂等键
     */
    private String extractFromBody(ProceedingJoinPoint joinPoint, String fieldName) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        // 遍历参数，查找包含指定字段的对象
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            try {
                JsonNode jsonNode = objectMapper.valueToTree(arg);
                if (jsonNode.has(fieldName)) {
                    return jsonNode.get(fieldName).asText();
                }
            } catch (Exception e) {
                log.debug("Failed to extract field {} from argument", fieldName);
            }
        }
        return null;
    }

    /**
     * 评估 SpEL 表达式
     */
    private String evaluateSpel(ProceedingJoinPoint joinPoint, String expression) {
        // TODO: 实现 SpEL 表达式解析
        // 可以使用 Spring 的 SpelExpressionParser
        return null;
    }

    /**
     * 解析存储的结果
     */
    private Object parseStoredResult(IdempotentRecord record, ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Class<?> returnType = method.getReturnType();
            
            return objectMapper.readValue(record.getResult(), returnType);
        } catch (Exception e) {
            log.error("[Idempotent] Failed to parse stored result", e);
            return Result.success(null, "请求已处理");
        }
    }

    /**
     * 获取当前请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}
