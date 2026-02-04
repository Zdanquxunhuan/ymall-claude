package com.yuge.fulfillment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.infrastructure.mapper.WaybillMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 运单仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WaybillRepository {

    private final WaybillMapper waybillMapper;

    /**
     * 保存运单
     */
    public void save(Waybill waybill) {
        waybillMapper.insert(waybill);
    }

    /**
     * 根据运单号查询
     */
    public Optional<Waybill> findByWaybillNo(String waybillNo) {
        Waybill waybill = waybillMapper.selectOne(
                new LambdaQueryWrapper<Waybill>()
                        .eq(Waybill::getWaybillNo, waybillNo)
        );
        return Optional.ofNullable(waybill);
    }

    /**
     * 根据发货单号查询
     */
    public Optional<Waybill> findByShipmentNo(String shipmentNo) {
        Waybill waybill = waybillMapper.selectOne(
                new LambdaQueryWrapper<Waybill>()
                        .eq(Waybill::getShipmentNo, shipmentNo)
                        .orderByDesc(Waybill::getCreatedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(waybill);
    }

    /**
     * 检查发货单是否已有运单
     */
    public boolean existsByShipmentNo(String shipmentNo) {
        Long count = waybillMapper.selectCount(
                new LambdaQueryWrapper<Waybill>()
                        .eq(Waybill::getShipmentNo, shipmentNo)
        );
        return count != null && count > 0;
    }
}
