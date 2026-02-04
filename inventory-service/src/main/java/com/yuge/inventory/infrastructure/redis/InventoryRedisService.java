package com.yuge.inventory.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存Redis服务
 * 使用Lua脚本实现原子性库存操作
 */
@Slf4j
@Component
public class InventoryRedisService {

    private static final String INV_KEY_PREFIX = "inv:";
    private static final String RESERVED_KEY_PREFIX = "inv:reserved:";
    
    /**
     * 幂等标记默认过期时间（秒）- 24小时
     */
    private static final long DEFAULT_EXPIRE_SECONDS = 86400L;

    private final StringRedisTemplate redisTemplate;

    private DefaultRedisScript<String> reserveScript;
    private DefaultRedisScript<String> releaseScript;
    private DefaultRedisScript<String> batchReserveScript;
    private DefaultRedisScript<String> batchReleaseScript;
    private DefaultRedisScript<String> syncScript;
    private DefaultRedisScript<String> getScript;

    public InventoryRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void init() {
        // 初始化预留脚本
        reserveScript = new DefaultRedisScript<>();
        reserveScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_reserve.lua")));
        reserveScript.setResultType(String.class);

        // 初始化释放脚本
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_release.lua")));
        releaseScript.setResultType(String.class);

        // 初始化批量预留脚本
        batchReserveScript = new DefaultRedisScript<>();
        batchReserveScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_batch_reserve.lua")));
        batchReserveScript.setResultType(String.class);

        // 初始化批量释放脚本
        batchReleaseScript = new DefaultRedisScript<>();
        batchReleaseScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_batch_release.lua")));
        batchReleaseScript.setResultType(String.class);

        // 初始化同步脚本
        syncScript = new DefaultRedisScript<>();
        syncScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_sync.lua")));
        syncScript.setResultType(String.class);

        // 初始化查询脚本
        getScript = new DefaultRedisScript<>();
        getScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/inventory_get.lua")));
        getScript.setResultType(String.class);

        log.info("[InventoryRedisService] Lua scripts initialized");
    }

    /**
     * 尝试预留库存（单个SKU）
     *
     * @param warehouseId 仓库ID
     * @param skuId       SKU ID
     * @param orderNo     订单号（用于幂等）
     * @param qty         预留数量
     * @return 预留结果
     */
    public ReserveResult tryReserve(Long warehouseId, Long skuId, String orderNo, int qty) {
        String invKey = buildInvKey(warehouseId, skuId);
        String reservedKey = buildReservedKey(orderNo, warehouseId, skuId);

        List<String> keys = List.of(invKey, reservedKey);
        List<String> args = List.of(String.valueOf(qty), String.valueOf(DEFAULT_EXPIRE_SECONDS));

        try {
            String result = redisTemplate.execute(reserveScript, keys, args.toArray(new String[0]));
            return parseReserveResult(result, 0);
        } catch (Exception e) {
            log.error("[InventoryRedisService] tryReserve failed, warehouseId={}, skuId={}, orderNo={}, qty={}, error={}",
                    warehouseId, skuId, orderNo, qty, e.getMessage(), e);
            return ReserveResult.error("Redis操作异常: " + e.getMessage());
        }
    }

    /**
     * 批量预留库存（多个SKU）
     *
     * @param orderNo 订单号
     * @param items   预留项列表
     * @return 预留结果
     */
    public ReserveResult tryBatchReserve(String orderNo, List<ReserveItem> items) {
        if (items == null || items.isEmpty()) {
            return ReserveResult.success();
        }

        int skuCount = items.size();
        List<String> keys = new ArrayList<>(skuCount * 2);
        List<String> args = new ArrayList<>(skuCount + 2);

        // 构建库存keys
        for (ReserveItem item : items) {
            keys.add(buildInvKey(item.getWarehouseId(), item.getSkuId()));
        }
        // 构建幂等标记keys
        for (ReserveItem item : items) {
            keys.add(buildReservedKey(orderNo, item.getWarehouseId(), item.getSkuId()));
        }

        // 构建参数
        args.add(String.valueOf(skuCount));
        for (ReserveItem item : items) {
            args.add(String.valueOf(item.getQty()));
        }
        args.add(String.valueOf(DEFAULT_EXPIRE_SECONDS));

        try {
            String result = redisTemplate.execute(batchReserveScript, keys, args.toArray(new String[0]));
            return parseBatchReserveResult(result, items);
        } catch (Exception e) {
            log.error("[InventoryRedisService] tryBatchReserve failed, orderNo={}, error={}",
                    orderNo, e.getMessage(), e);
            return ReserveResult.error("Redis操作异常: " + e.getMessage());
        }
    }

    /**
     * 释放预留库存（单个SKU）
     *
     * @param warehouseId 仓库ID
     * @param skuId       SKU ID
     * @param orderNo     订单号
     * @param qty         释放数量
     * @return 是否成功
     */
    public boolean release(Long warehouseId, Long skuId, String orderNo, int qty) {
        String invKey = buildInvKey(warehouseId, skuId);
        String reservedKey = buildReservedKey(orderNo, warehouseId, skuId);

        List<String> keys = List.of(invKey, reservedKey);
        List<String> args = List.of(String.valueOf(qty));

        try {
            String result = redisTemplate.execute(releaseScript, keys, args.toArray(new String[0]));
            long resultCode = Long.parseLong(result);
            
            if (resultCode == -1) {
                log.info("[InventoryRedisService] release skipped (idempotent), orderNo={}, skuId={}", orderNo, skuId);
                return true; // 幂等返回成功
            } else if (resultCode == -2) {
                log.warn("[InventoryRedisService] release failed, inventory key not found, skuId={}", skuId);
                return false;
            }
            
            log.info("[InventoryRedisService] release success, orderNo={}, skuId={}, qty={}, newAvailable={}",
                    orderNo, skuId, qty, resultCode);
            return true;
        } catch (Exception e) {
            log.error("[InventoryRedisService] release failed, warehouseId={}, skuId={}, orderNo={}, error={}",
                    warehouseId, skuId, orderNo, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量释放预留库存
     *
     * @param orderNo 订单号
     * @param items   释放项列表
     * @return 是否成功
     */
    public boolean batchRelease(String orderNo, List<ReserveItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        int skuCount = items.size();
        List<String> keys = new ArrayList<>(skuCount * 2);
        List<String> args = new ArrayList<>(skuCount + 1);

        // 构建库存keys
        for (ReserveItem item : items) {
            keys.add(buildInvKey(item.getWarehouseId(), item.getSkuId()));
        }
        // 构建幂等标记keys
        for (ReserveItem item : items) {
            keys.add(buildReservedKey(orderNo, item.getWarehouseId(), item.getSkuId()));
        }

        // 构建参数
        args.add(String.valueOf(skuCount));
        for (ReserveItem item : items) {
            args.add(String.valueOf(item.getQty()));
        }

        try {
            String result = redisTemplate.execute(batchReleaseScript, keys, args.toArray(new String[0]));
            log.info("[InventoryRedisService] batchRelease result={}, orderNo={}", result, orderNo);
            return "1".equals(result) || "0".equals(result); // 0表示幂等
        } catch (Exception e) {
            log.error("[InventoryRedisService] batchRelease failed, orderNo={}, error={}",
                    orderNo, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 同步库存到Redis
     *
     * @param warehouseId  仓库ID
     * @param skuId        SKU ID
     * @param availableQty 可用库存
     */
    public void syncInventory(Long warehouseId, Long skuId, int availableQty) {
        String invKey = buildInvKey(warehouseId, skuId);
        List<String> keys = List.of(invKey);
        List<String> args = List.of(String.valueOf(availableQty));

        try {
            redisTemplate.execute(syncScript, keys, args.toArray(new String[0]));
            log.debug("[InventoryRedisService] syncInventory success, warehouseId={}, skuId={}, available={}",
                    warehouseId, skuId, availableQty);
        } catch (Exception e) {
            log.error("[InventoryRedisService] syncInventory failed, warehouseId={}, skuId={}, error={}",
                    warehouseId, skuId, e.getMessage(), e);
            throw new RuntimeException("同步库存到Redis失败", e);
        }
    }

    /**
     * 查询Redis中的可用库存
     *
     * @param warehouseId 仓库ID
     * @param skuId       SKU ID
     * @return 可用库存，null表示不存在
     */
    public Integer getAvailableQty(Long warehouseId, Long skuId) {
        String invKey = buildInvKey(warehouseId, skuId);
        List<String> keys = List.of(invKey);

        try {
            String result = redisTemplate.execute(getScript, keys);
            return result != null ? Integer.parseInt(result) : null;
        } catch (Exception e) {
            log.error("[InventoryRedisService] getAvailableQty failed, warehouseId={}, skuId={}, error={}",
                    warehouseId, skuId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除库存缓存
     */
    public void deleteInventoryCache(Long warehouseId, Long skuId) {
        String invKey = buildInvKey(warehouseId, skuId);
        redisTemplate.delete(invKey);
    }

    /**
     * 构建库存key
     */
    private String buildInvKey(Long warehouseId, Long skuId) {
        return INV_KEY_PREFIX + warehouseId + ":" + skuId;
    }

    /**
     * 构建预留幂等标记key
     */
    private String buildReservedKey(String orderNo, Long warehouseId, Long skuId) {
        return RESERVED_KEY_PREFIX + orderNo + ":" + warehouseId + ":" + skuId;
    }

    /**
     * 解析单个预留结果
     */
    private ReserveResult parseReserveResult(String result, int skuIndex) {
        if (result == null) {
            return ReserveResult.error("Redis返回空结果");
        }

        try {
            long code = Long.parseLong(result);
            if (code == 0) {
                return ReserveResult.idempotent();
            } else if (code == -1) {
                return ReserveResult.insufficientStock(skuIndex);
            } else if (code == -2) {
                return ReserveResult.notFound(skuIndex);
            } else if (code > 0) {
                return ReserveResult.success();
            }
        } catch (NumberFormatException e) {
            // 可能是错误格式 "-1:0"
        }

        return ReserveResult.error("未知结果: " + result);
    }

    /**
     * 解析批量预留结果
     */
    private ReserveResult parseBatchReserveResult(String result, List<ReserveItem> items) {
        if (result == null) {
            return ReserveResult.error("Redis返回空结果");
        }

        if ("1".equals(result)) {
            return ReserveResult.success();
        } else if ("0".equals(result)) {
            return ReserveResult.idempotent();
        } else if (result.startsWith("-1:")) {
            int skuIndex = Integer.parseInt(result.substring(3));
            ReserveItem failedItem = items.get(skuIndex);
            return ReserveResult.insufficientStock(skuIndex, failedItem.getSkuId());
        } else if (result.startsWith("-2:")) {
            int skuIndex = Integer.parseInt(result.substring(3));
            ReserveItem failedItem = items.get(skuIndex);
            return ReserveResult.notFound(skuIndex, failedItem.getSkuId());
        }

        return ReserveResult.error("未知结果: " + result);
    }

    /**
     * 预留项
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReserveItem {
        private Long skuId;
        private Long warehouseId;
        private int qty;
    }

    /**
     * 预留结果
     */
    @lombok.Data
    @lombok.Builder
    public static class ReserveResult {
        private boolean success;
        private boolean idempotent;
        private boolean insufficientStock;
        private boolean notFound;
        private int failedSkuIndex;
        private Long failedSkuId;
        private String errorMessage;

        public static ReserveResult success() {
            return ReserveResult.builder().success(true).build();
        }

        public static ReserveResult idempotent() {
            return ReserveResult.builder().success(true).idempotent(true).build();
        }

        public static ReserveResult insufficientStock(int skuIndex) {
            return ReserveResult.builder()
                    .success(false)
                    .insufficientStock(true)
                    .failedSkuIndex(skuIndex)
                    .errorMessage("库存不足")
                    .build();
        }

        public static ReserveResult insufficientStock(int skuIndex, Long skuId) {
            return ReserveResult.builder()
                    .success(false)
                    .insufficientStock(true)
                    .failedSkuIndex(skuIndex)
                    .failedSkuId(skuId)
                    .errorMessage("SKU " + skuId + " 库存不足")
                    .build();
        }

        public static ReserveResult notFound(int skuIndex) {
            return ReserveResult.builder()
                    .success(false)
                    .notFound(true)
                    .failedSkuIndex(skuIndex)
                    .errorMessage("库存记录不存在")
                    .build();
        }

        public static ReserveResult notFound(int skuIndex, Long skuId) {
            return ReserveResult.builder()
                    .success(false)
                    .notFound(true)
                    .failedSkuIndex(skuIndex)
                    .failedSkuId(skuId)
                    .errorMessage("SKU " + skuId + " 库存记录不存在")
                    .build();
        }

        public static ReserveResult error(String message) {
            return ReserveResult.builder()
                    .success(false)
                    .errorMessage(message)
                    .build();
        }
    }
}
