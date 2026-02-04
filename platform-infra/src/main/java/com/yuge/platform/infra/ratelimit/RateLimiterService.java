package com.yuge.platform.infra.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 限流服务
 * 基于 Redis Lua 脚本实现令牌桶算法
 * 
 * 令牌桶算法特点：
 * 1. 以固定速率向桶中添加令牌
 * 2. 桶有最大容量，超出的令牌会被丢弃
 * 3. 请求到来时，从桶中取出令牌，没有令牌则拒绝
 * 4. 支持突发流量（桶中有积累的令牌时）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Redis Key 前缀
     */
    private static final String KEY_PREFIX = "ratelimit:";

    /**
     * 令牌桶 Lua 脚本
     * 
     * KEYS[1] = 限流键
     * ARGV[1] = 令牌桶容量 (capacity)
     * ARGV[2] = 每秒生成的令牌数 (rate)
     * ARGV[3] = 当前时间戳（毫秒）
     * ARGV[4] = 请求的令牌数（通常为1）
     * 
     * 返回: 1-允许通过, 0-被限流
     * 
     * 数据结构：
     * - tokens: 当前令牌数
     * - lastRefillTime: 上次填充时间
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local key = KEYS[1]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            
            -- 获取当前令牌数和上次填充时间
            local data = redis.call('HMGET', key, 'tokens', 'lastRefillTime')
            local tokens = tonumber(data[1])
            local lastRefillTime = tonumber(data[2])
            
            -- 初始化：如果是新的限流键，初始化为满桶
            if tokens == nil then
                tokens = capacity
                lastRefillTime = now
            end
            
            -- 计算需要添加的令牌数
            local elapsed = now - lastRefillTime
            local tokensToAdd = math.floor(elapsed * rate / 1000)
            
            -- 更新令牌数（不超过容量）
            if tokensToAdd > 0 then
                tokens = math.min(capacity, tokens + tokensToAdd)
                lastRefillTime = now
            end
            
            -- 判断是否有足够的令牌
            local allowed = 0
            if tokens >= requested then
                tokens = tokens - requested
                allowed = 1
            end
            
            -- 保存状态，设置过期时间（2倍的填满时间 + 1秒）
            local ttl = math.ceil(capacity / rate) * 2 + 1
            redis.call('HMSET', key, 'tokens', tokens, 'lastRefillTime', lastRefillTime)
            redis.call('EXPIRE', key, ttl)
            
            return allowed
            """;

    /**
     * 滑动窗口 Lua 脚本（备选方案）
     * 
     * KEYS[1] = 限流键
     * ARGV[1] = 窗口大小（毫秒）
     * ARGV[2] = 最大请求数
     * ARGV[3] = 当前时间戳（毫秒）
     * 
     * 返回: 1-允许通过, 0-被限流
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            
            -- 移除窗口外的请求记录
            local windowStart = now - window
            redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
            
            -- 获取当前窗口内的请求数
            local count = redis.call('ZCARD', key)
            
            -- 判断是否超过限制
            if count < limit then
                -- 添加当前请求
                redis.call('ZADD', key, now, now .. '-' .. math.random())
                redis.call('PEXPIRE', key, window)
                return 1
            else
                return 0
            end
            """;

    private final DefaultRedisScript<Long> tokenBucketScript;
    private final DefaultRedisScript<Long> slidingWindowScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        this.tokenBucketScript = new DefaultRedisScript<>();
        this.tokenBucketScript.setScriptText(TOKEN_BUCKET_SCRIPT);
        this.tokenBucketScript.setResultType(Long.class);
        
        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setScriptText(SLIDING_WINDOW_SCRIPT);
        this.slidingWindowScript.setResultType(Long.class);
    }

    /**
     * 令牌桶限流
     * 
     * @param key 限流键
     * @param qps 每秒允许的请求数
     * @param capacity 令牌桶容量
     * @return true-允许通过, false-被限流
     */
    public boolean tryAcquire(String key, int qps, int capacity) {
        return tryAcquire(key, qps, capacity, 1);
    }

    /**
     * 令牌桶限流（支持一次获取多个令牌）
     * 
     * @param key 限流键
     * @param qps 每秒允许的请求数
     * @param capacity 令牌桶容量
     * @param permits 请求的令牌数
     * @return true-允许通过, false-被限流
     */
    public boolean tryAcquire(String key, int qps, int capacity, int permits) {
        String fullKey = KEY_PREFIX + key;
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    tokenBucketScript,
                    Collections.singletonList(fullKey),
                    String.valueOf(capacity),
                    String.valueOf(qps),
                    String.valueOf(now),
                    String.valueOf(permits)
            );

            boolean allowed = result != null && result == 1L;
            
            if (!allowed) {
                log.warn("[RateLimit] Request rejected, key={}, qps={}, capacity={}", key, qps, capacity);
            } else {
                log.debug("[RateLimit] Request allowed, key={}", key);
            }
            
            return allowed;
        } catch (Exception e) {
            log.error("[RateLimit] Error executing rate limit script, key={}", key, e);
            // 限流组件异常时，默认放行（可根据业务需求调整）
            return true;
        }
    }

    /**
     * 滑动窗口限流
     * 
     * @param key 限流键
     * @param windowMs 窗口大小（毫秒）
     * @param limit 窗口内最大请求数
     * @return true-允许通过, false-被限流
     */
    public boolean tryAcquireSlidingWindow(String key, long windowMs, int limit) {
        String fullKey = KEY_PREFIX + "sw:" + key;
        long now = System.currentTimeMillis();

        try {
            Long result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(fullKey),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    String.valueOf(now)
            );

            boolean allowed = result != null && result == 1L;
            
            if (!allowed) {
                log.warn("[RateLimit] Sliding window rejected, key={}, window={}ms, limit={}", 
                        key, windowMs, limit);
            }
            
            return allowed;
        } catch (Exception e) {
            log.error("[RateLimit] Error executing sliding window script, key={}", key, e);
            return true;
        }
    }

    /**
     * 获取当前令牌数（用于监控）
     */
    public int getCurrentTokens(String key) {
        String fullKey = KEY_PREFIX + key;
        try {
            Object tokens = redisTemplate.opsForHash().get(fullKey, "tokens");
            return tokens != null ? Integer.parseInt(tokens.toString()) : -1;
        } catch (Exception e) {
            return -1;
        }
    }
}
