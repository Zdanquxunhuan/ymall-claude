package com.yuge.cart.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.cart.domain.entity.CartItem;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 购物车Redis服务
 * Key结构:
 * - 登录用户: cart:{userId} (Hash, field=skuId, value=CartItem JSON)
 * - 游客用户: cart:anon:{anonId} (Hash, field=skuId, value=CartItem JSON)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartRedisService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final String ANON_KEY_PREFIX = "cart:anon:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${cart.expire-days:30}")
    private int expireDays;

    @Value("${cart.max-items:100}")
    private int maxItems;

    @Value("${cart.max-qty-per-sku:99}")
    private int maxQtyPerSku;

    /**
     * 添加商品到购物车
     * 如果SKU已存在，则累加数量
     */
    public CartItem addItem(String cartKey, CartItem item) {
        String key = buildKey(cartKey);
        String field = String.valueOf(item.getSkuId());

        // 检查购物车商品数量限制
        Long size = redisTemplate.opsForHash().size(key);
        if (size != null && size >= maxItems && !redisTemplate.opsForHash().hasKey(key, field)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "购物车商品数量已达上限: " + maxItems);
        }

        // 查询是否已存在
        String existingJson = (String) redisTemplate.opsForHash().get(key, field);
        CartItem resultItem;

        if (existingJson != null) {
            // 已存在，累加数量
            CartItem existing = fromJson(existingJson);
            int newQty = Math.min(existing.getQty() + item.getQty(), maxQtyPerSku);
            existing.setQty(newQty);
            existing.setUpdatedAt(LocalDateTime.now());
            // 更新价格快照（使用最新价格）
            existing.setUnitPrice(item.getUnitPrice());
            existing.setTitle(item.getTitle());
            existing.setImageUrl(item.getImageUrl());
            resultItem = existing;
        } else {
            // 新增
            item.setQty(Math.min(item.getQty(), maxQtyPerSku));
            item.setAddedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            if (item.getChecked() == null) {
                item.setChecked(true);
            }
            resultItem = item;
        }

        redisTemplate.opsForHash().put(key, field, toJson(resultItem));
        refreshExpire(key);

        log.debug("[CartRedisService] addItem success, cartKey={}, skuId={}, qty={}",
                cartKey, item.getSkuId(), resultItem.getQty());

        return resultItem;
    }

    /**
     * 更新商品数量
     */
    public CartItem updateQty(String cartKey, Long skuId, int qty) {
        String key = buildKey(cartKey);
        String field = String.valueOf(skuId);

        String existingJson = (String) redisTemplate.opsForHash().get(key, field);
        if (existingJson == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "购物车中不存在该商品: " + skuId);
        }

        CartItem item = fromJson(existingJson);
        item.setQty(Math.min(Math.max(qty, 1), maxQtyPerSku));
        item.setUpdatedAt(LocalDateTime.now());

        redisTemplate.opsForHash().put(key, field, toJson(item));
        refreshExpire(key);

        log.debug("[CartRedisService] updateQty success, cartKey={}, skuId={}, qty={}",
                cartKey, skuId, qty);

        return item;
    }

    /**
     * 更新商品选中状态
     */
    public CartItem checkItem(String cartKey, Long skuId, boolean checked) {
        String key = buildKey(cartKey);
        String field = String.valueOf(skuId);

        String existingJson = (String) redisTemplate.opsForHash().get(key, field);
        if (existingJson == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "购物车中不存在该商品: " + skuId);
        }

        CartItem item = fromJson(existingJson);
        item.setChecked(checked);
        item.setUpdatedAt(LocalDateTime.now());

        redisTemplate.opsForHash().put(key, field, toJson(item));
        refreshExpire(key);

        log.debug("[CartRedisService] checkItem success, cartKey={}, skuId={}, checked={}",
                cartKey, skuId, checked);

        return item;
    }

    /**
     * 批量更新选中状态
     */
    public void checkAll(String cartKey, boolean checked) {
        String key = buildKey(cartKey);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return;
        }

        Map<String, String> updates = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            CartItem item = fromJson((String) entry.getValue());
            item.setChecked(checked);
            item.setUpdatedAt(LocalDateTime.now());
            updates.put((String) entry.getKey(), toJson(item));
        }

        redisTemplate.opsForHash().putAll(key, updates);
        refreshExpire(key);

        log.debug("[CartRedisService] checkAll success, cartKey={}, checked={}, count={}",
                cartKey, checked, updates.size());
    }

    /**
     * 移除商品
     */
    public void removeItem(String cartKey, Long skuId) {
        String key = buildKey(cartKey);
        String field = String.valueOf(skuId);

        Long deleted = redisTemplate.opsForHash().delete(key, field);

        log.debug("[CartRedisService] removeItem, cartKey={}, skuId={}, deleted={}",
                cartKey, skuId, deleted);
    }

    /**
     * 批量移除商品
     */
    public void removeItems(String cartKey, List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }

        String key = buildKey(cartKey);
        Object[] fields = skuIds.stream().map(String::valueOf).toArray();

        Long deleted = redisTemplate.opsForHash().delete(key, fields);

        log.debug("[CartRedisService] removeItems, cartKey={}, skuIds={}, deleted={}",
                cartKey, skuIds, deleted);
    }

    /**
     * 清空购物车
     */
    public void clear(String cartKey) {
        String key = buildKey(cartKey);
        redisTemplate.delete(key);

        log.debug("[CartRedisService] clear, cartKey={}", cartKey);
    }

    /**
     * 获取购物车所有商品
     */
    public List<CartItem> getAll(String cartKey) {
        String key = buildKey(cartKey);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        refreshExpire(key);

        return entries.values().stream()
                .map(v -> fromJson((String) v))
                .sorted(Comparator.comparing(CartItem::getUpdatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取选中的商品
     */
    public List<CartItem> getCheckedItems(String cartKey) {
        return getAll(cartKey).stream()
                .filter(item -> Boolean.TRUE.equals(item.getChecked()))
                .collect(Collectors.toList());
    }

    /**
     * 获取单个商品
     */
    public Optional<CartItem> getItem(String cartKey, Long skuId) {
        String key = buildKey(cartKey);
        String field = String.valueOf(skuId);

        String json = (String) redisTemplate.opsForHash().get(key, field);
        if (json == null) {
            return Optional.empty();
        }

        return Optional.of(fromJson(json));
    }

    /**
     * 获取购物车商品数量
     */
    public int getItemCount(String cartKey) {
        String key = buildKey(cartKey);
        Long size = redisTemplate.opsForHash().size(key);
        return size != null ? size.intValue() : 0;
    }

    /**
     * 批量保存商品（用于合并）
     */
    public void saveAll(String cartKey, List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        String key = buildKey(cartKey);
        Map<String, String> entries = new HashMap<>();

        for (CartItem item : items) {
            entries.put(String.valueOf(item.getSkuId()), toJson(item));
        }

        redisTemplate.opsForHash().putAll(key, entries);
        refreshExpire(key);

        log.debug("[CartRedisService] saveAll, cartKey={}, count={}", cartKey, items.size());
    }

    /**
     * 构建Redis Key
     */
    private String buildKey(String cartKey) {
        if (cartKey.startsWith("anon:")) {
            return ANON_KEY_PREFIX + cartKey.substring(5);
        }
        return CART_KEY_PREFIX + cartKey;
    }

    /**
     * 刷新过期时间
     */
    private void refreshExpire(String key) {
        redisTemplate.expire(key, Duration.ofDays(expireDays));
    }

    private String toJson(CartItem item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON序列化失败");
        }
    }

    private CartItem fromJson(String json) {
        try {
            return objectMapper.readValue(json, CartItem.class);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "JSON反序列化失败");
        }
    }

    /**
     * 构建用户购物车Key
     */
    public static String userCartKey(Long userId) {
        return String.valueOf(userId);
    }

    /**
     * 构建游客购物车Key
     */
    public static String anonCartKey(String anonId) {
        return "anon:" + anonId;
    }
}
