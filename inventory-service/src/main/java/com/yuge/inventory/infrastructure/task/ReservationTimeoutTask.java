package com.yuge.inventory.infrastructure.task;

import com.yuge.inventory.application.InventoryService;
import com.yuge.inventory.domain.entity.InventoryReservation;
import com.yuge.inventory.infrastructure.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 预留超时释放任务
 * 
 * 功能：
 * 1. 定时扫描已过期的预留记录
 * 2. 自动释放过期预留，归还库存
 * 
 * 配置：
 * - inventory.reservation.timeout.enabled: 是否启用（默认false）
 * - inventory.reservation.timeout.batch-size: 每批处理数量（默认100）
 * - inventory.reservation.timeout.cron: 执行周期（默认每分钟）
 * 
 * 注意事项：
 * 1. 生产环境建议使用分布式调度（如XXL-JOB）替代本地定时任务
 * 2. 需要配合分布式锁防止多实例重复执行
 * 3. 释放前应检查订单状态，避免误释放已支付订单的库存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTimeoutTask {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryService inventoryService;

    /**
     * 是否启用超时释放任务
     */
    @Value("${inventory.reservation.timeout.enabled:false}")
    private boolean enabled;

    /**
     * 每批处理数量
     */
    @Value("${inventory.reservation.timeout.batch-size:100}")
    private int batchSize;

    /**
     * 定时执行超时释放
     * 默认每分钟执行一次
     */
    @Scheduled(cron = "${inventory.reservation.timeout.cron:0 * * * * ?}")
    public void releaseExpiredReservations() {
        if (!enabled) {
            return;
        }

        log.info("[ReservationTimeoutTask] Start scanning expired reservations");
        long startTime = System.currentTimeMillis();
        int totalReleased = 0;
        int totalFailed = 0;

        try {
            // 分批处理过期预留
            List<InventoryReservation> expiredReservations;
            do {
                expiredReservations = reservationRepository.findExpiredReservations(batchSize);
                
                if (expiredReservations.isEmpty()) {
                    break;
                }

                log.info("[ReservationTimeoutTask] Found {} expired reservations", expiredReservations.size());

                // 按订单号分组释放
                for (InventoryReservation reservation : expiredReservations) {
                    try {
                        // 检查是否需要释放（可扩展：调用订单服务检查订单状态）
                        if (shouldRelease(reservation)) {
                            boolean released = inventoryService.releaseReservation(
                                    reservation.getOrderNo(), 
                                    "预留超时自动释放"
                            );
                            if (released) {
                                totalReleased++;
                                log.info("[ReservationTimeoutTask] Released reservation, orderNo={}, skuId={}",
                                        reservation.getOrderNo(), reservation.getSkuId());
                            }
                        }
                    } catch (Exception e) {
                        totalFailed++;
                        log.error("[ReservationTimeoutTask] Failed to release reservation, orderNo={}, skuId={}, error={}",
                                reservation.getOrderNo(), reservation.getSkuId(), e.getMessage(), e);
                    }
                }

            } while (!expiredReservations.isEmpty());

        } catch (Exception e) {
            log.error("[ReservationTimeoutTask] Task execution failed, error={}", e.getMessage(), e);
        }

        long costMs = System.currentTimeMillis() - startTime;
        log.info("[ReservationTimeoutTask] Task completed, released={}, failed={}, costMs={}",
                totalReleased, totalFailed, costMs);
    }

    /**
     * 判断是否应该释放预留
     * 
     * 可扩展逻辑：
     * 1. 调用订单服务检查订单状态
     * 2. 如果订单已支付，不应释放
     * 3. 如果订单已取消，应该释放
     * 4. 如果订单仍在待支付状态且已超时，应该释放
     */
    private boolean shouldRelease(InventoryReservation reservation) {
        // 基础检查：只释放已过期且状态为RESERVED的预留
        if (!reservation.isReserved()) {
            return false;
        }
        if (!reservation.isExpired()) {
            return false;
        }

        // TODO: 扩展点 - 调用订单服务检查订单状态
        // OrderStatus orderStatus = orderServiceClient.getOrderStatus(reservation.getOrderNo());
        // if (orderStatus == OrderStatus.PAID) {
        //     // 订单已支付，不应释放，而是应该确认预留
        //     inventoryService.confirmReservation(reservation.getOrderNo());
        //     return false;
        // }
        // if (orderStatus == OrderStatus.CANCELED) {
        //     return true;
        // }

        return true;
    }

    /**
     * 手动触发超时释放（用于测试/运维）
     */
    public void manualTrigger() {
        log.info("[ReservationTimeoutTask] Manual trigger started");
        boolean originalEnabled = this.enabled;
        try {
            this.enabled = true;
            releaseExpiredReservations();
        } finally {
            this.enabled = originalEnabled;
        }
    }
}
