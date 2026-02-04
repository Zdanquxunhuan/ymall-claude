package com.yuge.fulfillment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.fulfillment.domain.entity.Shipment;
import com.yuge.fulfillment.domain.enums.ShipmentStatus;
import com.yuge.fulfillment.infrastructure.mapper.ShipmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 发货单仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ShipmentRepository {

    private final ShipmentMapper shipmentMapper;

    /**
     * 保存发货单
     */
    public void save(Shipment shipment) {
        shipmentMapper.insert(shipment);
    }

    /**
     * 根据发货单号查询
     */
    public Optional<Shipment> findByShipmentNo(String shipmentNo) {
        Shipment shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<Shipment>()
                        .eq(Shipment::getShipmentNo, shipmentNo)
        );
        return Optional.ofNullable(shipment);
    }

    /**
     * 根据订单号查询
     */
    public Optional<Shipment> findByOrderNo(String orderNo) {
        Shipment shipment = shipmentMapper.selectOne(
                new LambdaQueryWrapper<Shipment>()
                        .eq(Shipment::getOrderNo, orderNo)
                        .orderByDesc(Shipment::getCreatedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(shipment);
    }

    /**
     * 检查订单是否已有发货单
     */
    public boolean existsByOrderNo(String orderNo) {
        Long count = shipmentMapper.selectCount(
                new LambdaQueryWrapper<Shipment>()
                        .eq(Shipment::getOrderNo, orderNo)
        );
        return count != null && count > 0;
    }

    /**
     * CAS更新状态为已发货
     */
    public boolean casUpdateStatusToShipped(String shipmentNo) {
        int updated = shipmentMapper.casUpdateStatusToShipped(
                shipmentNo,
                ShipmentStatus.CREATED.getCode(),
                ShipmentStatus.SHIPPED.getCode()
        );
        return updated > 0;
    }

    /**
     * CAS更新状态为已签收
     */
    public boolean casUpdateStatusToDelivered(String shipmentNo) {
        int updated = shipmentMapper.casUpdateStatusToDelivered(
                shipmentNo,
                ShipmentStatus.SHIPPED.getCode(),
                ShipmentStatus.DELIVERED.getCode()
        );
        return updated > 0;
    }
}
