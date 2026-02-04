package com.yuge.inventory.infrastructure.repository;

import com.yuge.inventory.domain.entity.InventoryTxn;
import com.yuge.inventory.infrastructure.mapper.InventoryTxnMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 库存流水仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryTxnRepository {

    private final InventoryTxnMapper txnMapper;

    /**
     * 保存流水
     */
    public void save(InventoryTxn txn) {
        txnMapper.insert(txn);
    }

    /**
     * 批量保存流水
     */
    public void saveBatch(List<InventoryTxn> txns) {
        for (InventoryTxn txn : txns) {
            txnMapper.insert(txn);
        }
    }

    /**
     * 根据订单号查询流水
     */
    public List<InventoryTxn> findByOrderNo(String orderNo) {
        return txnMapper.findByOrderNo(orderNo);
    }

    /**
     * 根据SKU和仓库查询流水
     */
    public List<InventoryTxn> findBySkuIdAndWarehouseId(Long skuId, Long warehouseId, int limit) {
        return txnMapper.findBySkuIdAndWarehouseId(skuId, warehouseId, limit);
    }

    /**
     * 根据流水ID查询
     */
    public InventoryTxn findByTxnId(String txnId) {
        return txnMapper.findByTxnId(txnId);
    }
}
