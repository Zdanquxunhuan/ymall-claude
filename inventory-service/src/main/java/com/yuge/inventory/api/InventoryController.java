package com.yuge.inventory.api;

import com.yuge.inventory.api.dto.InventoryResponse;
import com.yuge.inventory.api.dto.ReservationResponse;
import com.yuge.inventory.application.InventoryService;
import com.yuge.inventory.domain.entity.Inventory;
import com.yuge.inventory.domain.entity.InventoryReservation;
import com.yuge.inventory.domain.enums.ReservationStatus;
import com.yuge.inventory.infrastructure.redis.InventoryRedisService;
import com.yuge.platform.infra.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 库存控制器
 */
@Slf4j
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryRedisService redisService;

    /**
     * 查询库存
     * GET /inventory/{skuId}?warehouseId=1
     */
    @GetMapping("/{skuId}")
    public Result<InventoryResponse> getInventory(
            @PathVariable Long skuId,
            @RequestParam(required = false, defaultValue = "1") Long warehouseId) {
        
        log.info("[InventoryController] getInventory, skuId={}, warehouseId={}", skuId, warehouseId);

        Inventory inventory = inventoryService.getInventory(skuId, warehouseId)
                .orElse(null);

        if (inventory == null) {
            return Result.fail("404", "库存记录不存在");
        }

        // 同时查询Redis中的库存用于对比
        Integer redisAvailable = redisService.getAvailableQty(warehouseId, skuId);

        InventoryResponse response = InventoryResponse.builder()
                .id(inventory.getId())
                .skuId(inventory.getSkuId())
                .warehouseId(inventory.getWarehouseId())
                .availableQty(inventory.getAvailableQty())
                .reservedQty(inventory.getReservedQty())
                .totalQty(inventory.getTotalQty())
                .redisAvailableQty(redisAvailable)
                .updatedAt(inventory.getUpdatedAt())
                .build();

        return Result.ok(response);
    }

    /**
     * 查询SKU所有仓库库存
     * GET /inventory/{skuId}/all
     */
    @GetMapping("/{skuId}/all")
    public Result<List<InventoryResponse>> getInventoryBySkuId(@PathVariable Long skuId) {
        log.info("[InventoryController] getInventoryBySkuId, skuId={}", skuId);

        List<Inventory> inventories = inventoryService.getInventoryBySkuId(skuId);

        List<InventoryResponse> responses = inventories.stream()
                .map(inv -> {
                    Integer redisAvailable = redisService.getAvailableQty(inv.getWarehouseId(), inv.getSkuId());
                    return InventoryResponse.builder()
                            .id(inv.getId())
                            .skuId(inv.getSkuId())
                            .warehouseId(inv.getWarehouseId())
                            .availableQty(inv.getAvailableQty())
                            .reservedQty(inv.getReservedQty())
                            .totalQty(inv.getTotalQty())
                            .redisAvailableQty(redisAvailable)
                            .updatedAt(inv.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return Result.ok(responses);
    }

    /**
     * 查询订单预留记录
     * GET /inventory/reservations?orderNo=xxx
     */
    @GetMapping("/reservations")
    public Result<List<ReservationResponse>> getReservations(@RequestParam String orderNo) {
        log.info("[InventoryController] getReservations, orderNo={}", orderNo);

        List<InventoryReservation> reservations = inventoryService.getReservationsByOrderNo(orderNo);

        List<ReservationResponse> responses = reservations.stream()
                .map(this::toReservationResponse)
                .collect(Collectors.toList());

        return Result.ok(responses);
    }

    /**
     * 同步库存到Redis
     * POST /inventory/{skuId}/sync?warehouseId=1
     */
    @PostMapping("/{skuId}/sync")
    public Result<String> syncInventory(
            @PathVariable Long skuId,
            @RequestParam(required = false, defaultValue = "1") Long warehouseId) {
        
        log.info("[InventoryController] syncInventory, skuId={}, warehouseId={}", skuId, warehouseId);

        try {
            inventoryService.syncInventoryToRedis(skuId, warehouseId);
            return Result.ok("同步成功");
        } catch (Exception e) {
            log.error("[InventoryController] syncInventory failed, skuId={}, error={}", 
                    skuId, e.getMessage(), e);
            return Result.fail("500", "同步失败: " + e.getMessage());
        }
    }

    /**
     * 手动释放预留（用于测试/运维）
     * POST /inventory/reservations/release?orderNo=xxx
     */
    @PostMapping("/reservations/release")
    public Result<String> releaseReservation(
            @RequestParam String orderNo,
            @RequestParam(required = false, defaultValue = "手动释放") String reason) {
        
        log.info("[InventoryController] releaseReservation, orderNo={}, reason={}", orderNo, reason);

        try {
            boolean success = inventoryService.releaseReservation(orderNo, reason);
            if (success) {
                return Result.ok("释放成功");
            } else {
                return Result.fail("404", "未找到可释放的预留记录");
            }
        } catch (Exception e) {
            log.error("[InventoryController] releaseReservation failed, orderNo={}, error={}", 
                    orderNo, e.getMessage(), e);
            return Result.fail("500", "释放失败: " + e.getMessage());
        }
    }

    /**
     * 手动确认预留（用于测试/运维）
     * POST /inventory/reservations/confirm?orderNo=xxx
     */
    @PostMapping("/reservations/confirm")
    public Result<String> confirmReservation(@RequestParam String orderNo) {
        log.info("[InventoryController] confirmReservation, orderNo={}", orderNo);

        try {
            boolean success = inventoryService.confirmReservation(orderNo);
            if (success) {
                return Result.ok("确认成功");
            } else {
                return Result.fail("404", "未找到可确认的预留记录");
            }
        } catch (Exception e) {
            log.error("[InventoryController] confirmReservation failed, orderNo={}, error={}", 
                    orderNo, e.getMessage(), e);
            return Result.fail("500", "确认失败: " + e.getMessage());
        }
    }

    /**
     * 转换为预留响应DTO
     */
    private ReservationResponse toReservationResponse(InventoryReservation reservation) {
        ReservationStatus status = reservation.getStatusEnum();
        return ReservationResponse.builder()
                .id(reservation.getId())
                .orderNo(reservation.getOrderNo())
                .skuId(reservation.getSkuId())
                .warehouseId(reservation.getWarehouseId())
                .qty(reservation.getQty())
                .status(reservation.getStatus())
                .statusDesc(status != null ? status.getDesc() : null)
                .expireAt(reservation.getExpireAt())
                .expired(reservation.isExpired())
                .createdAt(reservation.getCreatedAt())
                .updatedAt(reservation.getUpdatedAt())
                .build();
    }
}
