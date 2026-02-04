package com.yuge.inventory.infrastructure.repository;

import com.yuge.inventory.domain.entity.Inventory;
import com.yuge.inventory.infrastructure.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 库存仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class InventoryRepository {

    private final InventoryMapper inventoryMapper;

    /**
     * 根据SKU和仓库查询库存
     */
    public Optional<Inventory> findBySkuIdAndWarehouseId(Long skuId, Long warehouseId) {
        return inventoryMapper.findBySkuIdAndWarehouseId(skuId, warehouseId);
    }

    /**
     * 根据SKU查询所有仓库库存
     */
    public List<Inventory> findBySkuId(Long skuId) {
        return inventoryMapper.findBySkuId(skuId);
    }

    /**
     * 根据仓库查询所有库存
     */
    public List<Inventory> findByWarehouseId(Long warehouseId) {
        return inventoryMapper.findByWarehouseId(warehouseId);
    }

    /**
     * 保存库存
     */
    public void save(Inventory inventory) {
        if (inventory.getId() == null) {
            inventoryMapper.insert(inventory);
        } else {
            inventoryMapper.updateById(inventory);
        }
    }

    /**
     * CAS预留库存
     */
    public boolean casReserve(Long skuId, Long warehouseId, int qty, int version) {
        int affected = inventoryMapper.casReserve(skuId, warehouseId, qty, version);
        return affected > 0;
    }

    /**
     * CAS确认库存
     */
    public boolean casConfirm(Long skuId, Long warehouseId, int qty, int version) {
        int affected = inventoryMapper.casConfirm(skuId, warehouseId, qty, version);
        return affected > 0;
    }

    /**
     * CAS释放库存
     */
    public boolean casRelease(Long skuId, Long warehouseId, int qty, int version) {
        int affected = inventoryMapper.casRelease(skuId, warehouseId, qty, version);
        return affected > 0;
    }

    /**
     * 更新可用库存
     */
    public boolean updateAvailableQty(Long skuId, Long warehouseId, int availableQty) {
        int affected = inventoryMapper.updateAvailableQty(skuId, warehouseId, availableQty);
        return affected > 0;
    }
}
