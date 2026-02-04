package com.yuge.inventory.application;

import com.yuge.inventory.domain.entity.Inventory;
import com.yuge.inventory.domain.entity.InventoryReservation;
import com.yuge.inventory.domain.entity.InventoryTxn;
import com.yuge.inventory.domain.enums.ReservationStatus;
import com.yuge.inventory.domain.enums.StockErrorCode;
import com.yuge.inventory.infrastructure.redis.InventoryRedisService;
import com.yuge.inventory.infrastructure.redis.InventoryRedisService.ReserveItem;
import com.yuge.inventory.infrastructure.redis.InventoryRedisService.ReserveResult;
import com.yuge.inventory.infrastructure.repository.InventoryRepository;
import com.yuge.inventory.infrastructure.repository.InventoryReservationRepository;
import com.yuge.inventory.infrastructure.repository.InventoryTxnRepository;
import com.yuge.platform.infra.trace.TraceContext;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 库存服务
 * 
 * 核心流程：
 * 1. TryReserve: Redis Lua原子预扣 -> 落库reservation（幂等）
 * 2. Confirm: 更新reservation状态 -> 更新DB库存
 * 3. Release: 更新reservation状态 -> Redis归还 -> 更新DB库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    /**
     * 预留过期时间（分钟）
     */
    private static final int RESERVATION_EXPIRE_MINUTES = 30;

    private final InventoryRedisService redisService;
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryTxnRepository txnRepository;

    /**
     * 尝试预留库存（单个SKU）
     * 
     * 流程：
     * 1. Redis Lua原子检查并扣减可用库存
     * 2. 落库reservation记录（幂等：唯一约束）
     * 3. 更新DB库存（CAS）
     * 4. 记录流水
     *
     * @param orderNo     订单号
     * @param skuId       SKU ID
     * @param warehouseId 仓库ID
     * @param qty         预留数量
     * @return 预留结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReserveResponse tryReserve(String orderNo, Long skuId, Long warehouseId, int qty) {
        log.info("[InventoryService] tryReserve start, orderNo={}, skuId={}, warehouseId={}, qty={}",
                orderNo, skuId, warehouseId, qty);

        // 1. 幂等检查：查询是否已有预留记录
        Optional<InventoryReservation> existingReservation = 
                reservationRepository.findByOrderNoAndSkuIdAndWarehouseId(orderNo, skuId, warehouseId);
        
        if (existingReservation.isPresent()) {
            InventoryReservation reservation = existingReservation.get();
            if (reservation.isReserved() || reservation.isConfirmed()) {
                log.info("[InventoryService] tryReserve idempotent, orderNo={}, skuId={}, status={}",
                        orderNo, skuId, reservation.getStatus());
                return ReserveResponse.success(orderNo, "已预留（幂等）");
            }
            // RELEASED状态不应该再次预留
            if (reservation.isReleased()) {
                log.warn("[InventoryService] tryReserve failed, reservation already released, orderNo={}, skuId={}",
                        orderNo, skuId);
                return ReserveResponse.fail(orderNo, StockErrorCode.INVALID_RESERVATION_STATUS, 
                        "预留已释放，不可重复预留");
            }
        }

        // 2. Redis Lua原子预扣
        ReserveResult redisResult = redisService.tryReserve(warehouseId, skuId, orderNo, qty);
        
        if (!redisResult.isSuccess()) {
            log.warn("[InventoryService] tryReserve Redis failed, orderNo={}, skuId={}, error={}",
                    orderNo, skuId, redisResult.getErrorMessage());
            
            if (redisResult.isInsufficientStock()) {
                return ReserveResponse.fail(orderNo, StockErrorCode.INSUFFICIENT_STOCK, 
                        "SKU " + skuId + " 库存不足");
            } else if (redisResult.isNotFound()) {
                return ReserveResponse.fail(orderNo, StockErrorCode.INVENTORY_NOT_FOUND, 
                        "SKU " + skuId + " 库存记录不存在");
            }
            return ReserveResponse.fail(orderNo, StockErrorCode.SYSTEM_ERROR, redisResult.getErrorMessage());
        }

        // 3. 幂等返回
        if (redisResult.isIdempotent()) {
            log.info("[InventoryService] tryReserve Redis idempotent, orderNo={}, skuId={}", orderNo, skuId);
            return ReserveResponse.success(orderNo, "已预留（Redis幂等）");
        }

        try {
            // 4. 落库reservation记录
            InventoryReservation reservation = new InventoryReservation();
            reservation.setOrderNo(orderNo);
            reservation.setSkuId(skuId);
            reservation.setWarehouseId(warehouseId);
            reservation.setQty(qty);
            reservation.setStatus(ReservationStatus.RESERVED.getCode());
            reservation.setExpireAt(LocalDateTime.now().plusMinutes(RESERVATION_EXPIRE_MINUTES));
            reservation.setVersion(0);
            reservation.setDeleted(0);

            try {
                reservationRepository.save(reservation);
            } catch (DuplicateKeyException e) {
                // 并发插入，幂等返回
                log.info("[InventoryService] tryReserve reservation duplicate, orderNo={}, skuId={}", 
                        orderNo, skuId);
                return ReserveResponse.success(orderNo, "已预留（DB幂等）");
            }

            // 5. 更新DB库存（CAS）
            Inventory inventory = inventoryRepository.findBySkuIdAndWarehouseId(skuId, warehouseId)
                    .orElseThrow(() -> new RuntimeException("库存记录不存在: skuId=" + skuId));
            
            boolean dbUpdated = inventoryRepository.casReserve(skuId, warehouseId, qty, inventory.getVersion());
            if (!dbUpdated) {
                log.warn("[InventoryService] tryReserve DB CAS failed, orderNo={}, skuId={}", orderNo, skuId);
                // DB更新失败，但Redis已扣减，需要补偿
                // 这里选择继续成功，因为Redis是主要的库存控制点
                // 后续可以通过对账任务修复DB
            }

            // 6. 记录流水
            Inventory updatedInventory = inventoryRepository.findBySkuIdAndWarehouseId(skuId, warehouseId)
                    .orElse(inventory);
            InventoryTxn txn = InventoryTxn.buildReserveTxn(
                    IdUtil.fastSimpleUUID(),
                    orderNo,
                    skuId,
                    warehouseId,
                    qty,
                    updatedInventory.getAvailableQty(),
                    updatedInventory.getReservedQty(),
                    TraceContext.getTraceId()
            );
            txnRepository.save(txn);

            log.info("[InventoryService] tryReserve success, orderNo={}, skuId={}, qty={}", 
                    orderNo, skuId, qty);
            return ReserveResponse.success(orderNo, "预留成功");

        } catch (Exception e) {
            log.error("[InventoryService] tryReserve failed after Redis success, orderNo={}, skuId={}, error={}",
                    orderNo, skuId, e.getMessage(), e);
            // Redis已扣减但DB操作失败，需要回滚Redis
            redisService.release(warehouseId, skuId, orderNo, qty);
            throw e;
        }
    }

    /**
     * 批量预留库存（多个SKU）
     */
    @Transactional(rollbackFor = Exception.class)
    public ReserveResponse tryBatchReserve(String orderNo, List<ReserveItem> items) {
        log.info("[InventoryService] tryBatchReserve start, orderNo={}, itemCount={}", 
                orderNo, items.size());

        // 1. 幂等检查
        List<InventoryReservation> existingReservations = reservationRepository.findByOrderNo(orderNo);
        if (!existingReservations.isEmpty()) {
            boolean allReservedOrConfirmed = existingReservations.stream()
                    .allMatch(r -> r.isReserved() || r.isConfirmed());
            if (allReservedOrConfirmed && existingReservations.size() == items.size()) {
                log.info("[InventoryService] tryBatchReserve idempotent, orderNo={}", orderNo);
                return ReserveResponse.success(orderNo, "已预留（幂等）");
            }
        }

        // 2. Redis Lua批量原子预扣
        ReserveResult redisResult = redisService.tryBatchReserve(orderNo, items);
        
        if (!redisResult.isSuccess()) {
            log.warn("[InventoryService] tryBatchReserve Redis failed, orderNo={}, error={}",
                    orderNo, redisResult.getErrorMessage());
            
            if (redisResult.isInsufficientStock()) {
                return ReserveResponse.fail(orderNo, StockErrorCode.INSUFFICIENT_STOCK, 
                        redisResult.getErrorMessage());
            } else if (redisResult.isNotFound()) {
                return ReserveResponse.fail(orderNo, StockErrorCode.INVENTORY_NOT_FOUND, 
                        redisResult.getErrorMessage());
            }
            return ReserveResponse.fail(orderNo, StockErrorCode.SYSTEM_ERROR, redisResult.getErrorMessage());
        }

        if (redisResult.isIdempotent()) {
            log.info("[InventoryService] tryBatchReserve Redis idempotent, orderNo={}", orderNo);
            return ReserveResponse.success(orderNo, "已预留（Redis幂等）");
        }

        try {
            // 3. 批量落库reservation记录
            List<InventoryTxn> txns = new ArrayList<>();
            LocalDateTime expireAt = LocalDateTime.now().plusMinutes(RESERVATION_EXPIRE_MINUTES);

            for (ReserveItem item : items) {
                // 保存reservation
                InventoryReservation reservation = new InventoryReservation();
                reservation.setOrderNo(orderNo);
                reservation.setSkuId(item.getSkuId());
                reservation.setWarehouseId(item.getWarehouseId());
                reservation.setQty(item.getQty());
                reservation.setStatus(ReservationStatus.RESERVED.getCode());
                reservation.setExpireAt(expireAt);
                reservation.setVersion(0);
                reservation.setDeleted(0);

                try {
                    reservationRepository.save(reservation);
                } catch (DuplicateKeyException e) {
                    log.info("[InventoryService] tryBatchReserve reservation duplicate, orderNo={}, skuId={}", 
                            orderNo, item.getSkuId());
                    continue;
                }

                // 更新DB库存
                Inventory inventory = inventoryRepository.findBySkuIdAndWarehouseId(
                        item.getSkuId(), item.getWarehouseId()).orElse(null);
                if (inventory != null) {
                    inventoryRepository.casReserve(item.getSkuId(), item.getWarehouseId(), 
                            item.getQty(), inventory.getVersion());
                    
                    // 构建流水
                    Inventory updated = inventoryRepository.findBySkuIdAndWarehouseId(
                            item.getSkuId(), item.getWarehouseId()).orElse(inventory);
                    txns.add(InventoryTxn.buildReserveTxn(
                            IdUtil.fastSimpleUUID(),
                            orderNo,
                            item.getSkuId(),
                            item.getWarehouseId(),
                            item.getQty(),
                            updated.getAvailableQty(),
                            updated.getReservedQty(),
                            TraceContext.getTraceId()
                    ));
                }
            }

            // 4. 批量保存流水
            if (!txns.isEmpty()) {
                txnRepository.saveBatch(txns);
            }

            log.info("[InventoryService] tryBatchReserve success, orderNo={}, itemCount={}", 
                    orderNo, items.size());
            return ReserveResponse.success(orderNo, "批量预留成功");

        } catch (Exception e) {
            log.error("[InventoryService] tryBatchReserve failed after Redis success, orderNo={}, error={}",
                    orderNo, e.getMessage(), e);
            // 回滚Redis
            redisService.batchRelease(orderNo, items);
            throw e;
        }
    }

    /**
     * 确认预留（订单支付成功后调用）
     * 
     * 流程：
     * 1. 更新reservation状态为CONFIRMED
     * 2. 更新DB库存（reserved减少）
     * 3. 记录流水
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean confirmReservation(String orderNo) {
        log.info("[InventoryService] confirmReservation start, orderNo={}", orderNo);

        List<InventoryReservation> reservations = 
                reservationRepository.findByOrderNoAndStatus(orderNo, ReservationStatus.RESERVED);
        
        if (reservations.isEmpty()) {
            // 检查是否已确认（幂等）
            List<InventoryReservation> confirmed = 
                    reservationRepository.findByOrderNoAndStatus(orderNo, ReservationStatus.CONFIRMED);
            if (!confirmed.isEmpty()) {
                log.info("[InventoryService] confirmReservation idempotent, orderNo={}", orderNo);
                return true;
            }
            log.warn("[InventoryService] confirmReservation no reservation found, orderNo={}", orderNo);
            return false;
        }

        List<InventoryTxn> txns = new ArrayList<>();

        for (InventoryReservation reservation : reservations) {
            // 更新reservation状态
            boolean updated = reservationRepository.casUpdateStatus(
                    reservation.getId(),
                    ReservationStatus.RESERVED,
                    ReservationStatus.CONFIRMED,
                    reservation.getVersion()
            );

            if (!updated) {
                log.warn("[InventoryService] confirmReservation CAS failed, orderNo={}, skuId={}",
                        orderNo, reservation.getSkuId());
                continue;
            }

            // 更新DB库存
            Inventory inventory = inventoryRepository.findBySkuIdAndWarehouseId(
                    reservation.getSkuId(), reservation.getWarehouseId()).orElse(null);
            if (inventory != null) {
                inventoryRepository.casConfirm(reservation.getSkuId(), reservation.getWarehouseId(),
                        reservation.getQty(), inventory.getVersion());

                // 记录流水
                Inventory updated2 = inventoryRepository.findBySkuIdAndWarehouseId(
                        reservation.getSkuId(), reservation.getWarehouseId()).orElse(inventory);
                txns.add(InventoryTxn.buildConfirmTxn(
                        IdUtil.fastSimpleUUID(),
                        orderNo,
                        reservation.getSkuId(),
                        reservation.getWarehouseId(),
                        reservation.getQty(),
                        updated2.getAvailableQty(),
                        updated2.getReservedQty(),
                        TraceContext.getTraceId()
                ));
            }
        }

        if (!txns.isEmpty()) {
            txnRepository.saveBatch(txns);
        }

        log.info("[InventoryService] confirmReservation success, orderNo={}", orderNo);
        return true;
    }

    /**
     * 释放预留（订单取消或超时）
     * 
     * 流程：
     * 1. 更新reservation状态为RELEASED
     * 2. Redis归还库存
     * 3. 更新DB库存（available增加，reserved减少）
     * 4. 记录流水
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean releaseReservation(String orderNo, String reason) {
        log.info("[InventoryService] releaseReservation start, orderNo={}, reason={}", orderNo, reason);

        List<InventoryReservation> reservations = 
                reservationRepository.findByOrderNoAndStatus(orderNo, ReservationStatus.RESERVED);
        
        if (reservations.isEmpty()) {
            // 检查是否已释放（幂等）
            List<InventoryReservation> released = 
                    reservationRepository.findByOrderNoAndStatus(orderNo, ReservationStatus.RELEASED);
            if (!released.isEmpty()) {
                log.info("[InventoryService] releaseReservation idempotent, orderNo={}", orderNo);
                return true;
            }
            log.warn("[InventoryService] releaseReservation no reservation found, orderNo={}", orderNo);
            return false;
        }

        List<InventoryTxn> txns = new ArrayList<>();
        List<ReserveItem> releaseItems = new ArrayList<>();

        for (InventoryReservation reservation : reservations) {
            // 更新reservation状态
            boolean updated = reservationRepository.casUpdateStatus(
                    reservation.getId(),
                    ReservationStatus.RESERVED,
                    ReservationStatus.RELEASED,
                    reservation.getVersion()
            );

            if (!updated) {
                log.warn("[InventoryService] releaseReservation CAS failed, orderNo={}, skuId={}",
                        orderNo, reservation.getSkuId());
                continue;
            }

            releaseItems.add(ReserveItem.builder()
                    .skuId(reservation.getSkuId())
                    .warehouseId(reservation.getWarehouseId())
                    .qty(reservation.getQty())
                    .build());

            // 更新DB库存
            Inventory inventory = inventoryRepository.findBySkuIdAndWarehouseId(
                    reservation.getSkuId(), reservation.getWarehouseId()).orElse(null);
            if (inventory != null) {
                inventoryRepository.casRelease(reservation.getSkuId(), reservation.getWarehouseId(),
                        reservation.getQty(), inventory.getVersion());

                // 记录流水
                Inventory updated2 = inventoryRepository.findBySkuIdAndWarehouseId(
                        reservation.getSkuId(), reservation.getWarehouseId()).orElse(inventory);
                txns.add(InventoryTxn.buildReleaseTxn(
                        IdUtil.fastSimpleUUID(),
                        orderNo,
                        reservation.getSkuId(),
                        reservation.getWarehouseId(),
                        reservation.getQty(),
                        updated2.getAvailableQty(),
                        updated2.getReservedQty(),
                        reason,
                        TraceContext.getTraceId()
                ));
            }
        }

        // Redis批量释放
        if (!releaseItems.isEmpty()) {
            redisService.batchRelease(orderNo, releaseItems);
        }

        if (!txns.isEmpty()) {
            txnRepository.saveBatch(txns);
        }

        log.info("[InventoryService] releaseReservation success, orderNo={}", orderNo);
        return true;
    }

    /**
     * 查询库存
     */
    public Optional<Inventory> getInventory(Long skuId, Long warehouseId) {
        return inventoryRepository.findBySkuIdAndWarehouseId(skuId, warehouseId);
    }

    /**
     * 查询SKU所有仓库库存
     */
    public List<Inventory> getInventoryBySkuId(Long skuId) {
        return inventoryRepository.findBySkuId(skuId);
    }

    /**
     * 查询订单预留记录
     */
    public List<InventoryReservation> getReservationsByOrderNo(String orderNo) {
        return reservationRepository.findByOrderNo(orderNo);
    }

    /**
     * 同步库存到Redis
     */
    public void syncInventoryToRedis(Long skuId, Long warehouseId) {
        Inventory inventory = inventoryRepository.findBySkuIdAndWarehouseId(skuId, warehouseId)
                .orElseThrow(() -> new RuntimeException("库存记录不存在"));
        redisService.syncInventory(warehouseId, skuId, inventory.getAvailableQty());
        log.info("[InventoryService] syncInventoryToRedis success, skuId={}, warehouseId={}, available={}",
                skuId, warehouseId, inventory.getAvailableQty());
    }

    /**
     * 预留响应
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReserveResponse {
        private boolean success;
        private String orderNo;
        private String message;
        private StockErrorCode errorCode;

        public static ReserveResponse success(String orderNo, String message) {
            return ReserveResponse.builder()
                    .success(true)
                    .orderNo(orderNo)
                    .message(message)
                    .build();
        }

        public static ReserveResponse fail(String orderNo, StockErrorCode errorCode, String message) {
            return ReserveResponse.builder()
                    .success(false)
                    .orderNo(orderNo)
                    .errorCode(errorCode)
                    .message(message)
                    .build();
        }
    }
}
