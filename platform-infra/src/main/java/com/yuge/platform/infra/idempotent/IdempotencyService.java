package com.yuge.platform.infra.idempotent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 幂等服务
 * 基于 Redis 实现，支持 TTL、并发安全
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis Key 前缀
     */
    private static final String KEY_PREFIX = "idempotent:";

    /**
     * Lua 脚本：原子性设置处理中状态（仅当 key 不存在时）
     * KEYS[1] = key
     * ARGV[1] = value (JSON)
     * ARGV[2] = ttl (seconds)
     * 返回: 1-设置成功, 0-key已存在
     */
    private static final String SET_IF_ABSENT_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                return 1
            else
                return 0
            end
            """;

    /**
     * Lua 脚本：原子性更新状态（仅当状态为 PROCESSING 时）
     * KEYS[1] = key
     * ARGV[1] = new value (JSON)
     * ARGV[2] = expected status (PROCESSING)
     * 返回: 1-更新成功, 0-状态不匹配或key不存在
     */
    private static final String UPDATE_IF_PROCESSING_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current then
                local data = cjson.decode(current)
                if data.status == ARGV[2] then
                    local ttl = redis.call('TTL', KEYS[1])
                    if ttl > 0 then
                        redis.call('SET', KEYS[1], ARGV[1], 'EX', ttl)
                    else
                        redis.call('SET', KEYS[1], ARGV[1])
                    end
                    return 1
                end
            end
            return 0
            """;

    /**
     * 尝试获取幂等锁
     * 
     * @param key 幂等键
     * @param timeout 超时时间
     * @param timeUnit 时间单位
     * @return 获取结果：empty-获取成功，present-已存在记录
     */
    public Optional<IdempotentRecord> tryAcquire(String key, long timeout, TimeUnit timeUnit) {
        String fullKey = KEY_PREFIX + key;
        long ttlSeconds = timeUnit.toSeconds(timeout);
        long now = System.currentTimeMillis();

        // 先检查是否已存在
        String existing = redisTemplate.opsForValue().get(fullKey);
        if (existing != null) {
            try {
                IdempotentRecord record = objectMapper.readValue(existing, IdempotentRecord.class);
                log.info("[Idempotent] Key already exists, key={}, status={}, traceId={}",
                        key, record.getStatus(), record.getTraceId());
                return Optional.of(record);
            } catch (JsonProcessingException e) {
                log.error("[Idempotent] Failed to parse existing record, key={}", key, e);
            }
        }

        // 构建处理中记录
        IdempotentRecord record = IdempotentRecord.builder()
                .key(key)
                .status(IdempotentRecord.Status.PROCESSING)
                .createdAt(now)
                .expireAt(now + timeUnit.toMillis(timeout))
                .traceId(TraceContext.getTraceId())
                .build();

        try {
            String value = objectMapper.writeValueAsString(record);
            
            // 使用 Lua 脚本原子性设置
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SET_IF_ABSENT_SCRIPT, Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(fullKey), value, String.valueOf(ttlSeconds));

            if (result != null && result == 1L) {
                log.info("[Idempotent] Acquired lock, key={}, ttl={}s, traceId={}",
                        key, ttlSeconds, TraceContext.getTraceId());
                return Optional.empty(); // 获取成功
            } else {
                // 再次获取已存在的记录
                existing = redisTemplate.opsForValue().get(fullKey);
                if (existing != null) {
                    IdempotentRecord existingRecord = objectMapper.readValue(existing, IdempotentRecord.class);
                    log.info("[Idempotent] Key exists after race, key={}, status={}", key, existingRecord.getStatus());
                    return Optional.of(existingRecord);
                }
            }
        } catch (JsonProcessingException e) {
            log.error("[Idempotent] Failed to serialize record, key={}", key, e);
        }

        return Optional.empty();
    }

    /**
     * 标记处理成功并存储结果
     */
    public void markSuccess(String key, Object result) {
        updateStatus(key, IdempotentRecord.Status.SUCCESS, result);  }

    /**
     * 标记处理失败
     */
    public void markFailed(String key) {
        updateStatus(key, IdempotentRecord.Status.FAILED, null);
    }

    /**
     * 释放幂等锁（处理失败时调用，允许重试）
     */
    public void release(String key) {
        String fullKey = KEY_PREFIX + key;
        redisTemplate.delete(fullKey);
        log.info("[Idempotent] Released lock, key={}", key);
    }

    /**
     * 获取幂等记录
     */
    public Optional<IdempotentRecord> getRecord(String key) {
        String fullKey = KEY_PREFIX + key;
        String value = redisTemplate.opsForValue().get(fullKey);
        if (value != null) {
            try {
                return Optional.of(objectMapper.readValue(value, IdempotentRecord.class));
            } catch (JsonProcessingException e) {
                log.error("[Idempotent] Failed to parse record, key={}", key, e);
            }
        }
        return Optional.empty();
    }

    /**
     * 更新状态
     */
    private void updateStatus(String key, IdempotentRecord.Status status, Object result) {
        String fullKey = KEY_PREFIX + key;
        
        try {
            // 获取当前记录
            String existing = redisTemplate.opsForValue().get(fullKey);
            if (existing == null) {
                log.warn("[Idempotent] Record not found when updating, key={}", key);
                return;
            }

            IdempotentRecord record = objectMapper.readValue(existing, IdempotentRecord.class);
            record.setStatus(status);
            
            if (result != null) {
                record.setResult(objectMapper.writeValueAsString(result));
                record.setResultType(result.getClass().getName());
            }

            // 保持原有 TTL
            Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                redisTemplate.opsForValue().set(fullKey, objectMapper.writeValueAsString(record), ttl, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(fullKey, objectMapper.writeValueAsString(record));
            }

            log.info("[Idempotent] Updated status, key={}, status={}", key, status);
        } catch (JsonProcessingException e) {
            log.error("[Idempotent] Failed to update status, key={}", key, e);
        }
    }

    /**
     * 解析存储的结果
     */
    @SuppressWarnings("unchecked")
    public <T> T parseResult(IdempotentRecord record, Class<T> clazz) {
        if (record.getResult() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(record.getResult(), clazz);
        } catch (JsonProcessingException e) {
            log.error("[Idempotent] Failed to parse result, key={}", record.getKey(), e);
            return null;
        }
    }
}
