package com.yuge.inventory.infrastructure.repository;

import com.yuge.inventory.domain.entity.InventoryReservation;
import com.yuge.inventory.domain.enums.ReservationStatus;
import com.yuge.inventory.infrastructure.mapper.InventoryReservationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 库存预留仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryReservationRepository {

    private final InventoryReservationMapper reservationMapper;

    /**
     * 根据订单号、SKU、仓库查询预留记录
     */
    public Optional<InventoryReservation> findByOrderNoAndSkuIdAndWarehouseId(
            String orderNo, Long skuId, Long warehouseId) {
        return reservationMapper.findByOrderNoAndSkuIdAndWarehouseId(orderNo, skuId, warehouseId);
    }

    /**
     * 根据订单号查询所有预留记录
     */
    public List<InventoryReservation> findByOrderNo(String orderNo) {
        return reservationMapper.findByOrderNo(orderNo);
    }

    /**
     * 根据订单号和状态查询预留记录
     */
    public List<InventoryReservation> findByOrderNoAndStatus(String orderNo, ReservationStatus status) {
        return reservationMapper.findByOrderNoAndStatus(orderNo, status.getCode());
    }

    /**
     * 查询已过期的预留记录
     */
    public List<InventoryReservation> findExpiredReservations(int limit) {
        return reservationMapper.findExpiredReservations(LocalDateTime.now(), limit);
    }

    /**
     * 保存预留记录
     */
    public void save(InventoryReservation reservation) {
        if (reservation.getId() == null) {
            reservationMapper.insert(reservation);
        } else {
            reservationMapper.updateById(reservation);
        }
    }

    /**
     * CAS更新预留状态
     */
    public boolean casUpdateStatus(Long id, ReservationStatus oldStatus, 
                                    ReservationStatus newStatus, int version) {
        int affected = reservationMapper.casUpdateStatus(id, oldStatus.getCode(), 
                newStatus.getCode(), version);
        return affected > 0;
    }

    /**
     * 批量更新订单的预留状态
     */
    public int batchUpdateStatusByOrderNo(String orderNo, ReservationStatus oldStatus, 
                                           ReservationStatus newStatus) {
        return reservationMapper.batchUpdateStatusByOrderNo(orderNo, 
                oldStatus.getCode(), newStatus.getCode());
    }
}
