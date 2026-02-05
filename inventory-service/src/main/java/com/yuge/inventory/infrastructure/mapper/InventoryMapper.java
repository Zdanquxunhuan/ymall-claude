package com.yuge.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.inventory.domain.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * 库存Mapper
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 根据SKU和仓库查询库存
     */
    @Select("SELECT * FROM t_inventory WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND deleted = 0")
    Optional<Inventory> findBySkuIdAndWarehouseId(@Param("skuId") Long skuId, 
                                                   @Param("warehouseId") Long warehouseId);

    /**
     * 根据SKU查询所有仓库库存
     */
    @Select("SELECT * FROM t_inventory WHERE sku_id = #{skuId} AND deleted = 0")
    List<Inventory> findBySkuId(@Param("skuId") Long skuId);

    /**
     * 根据仓库查询所有库存
     */
    @Select("SELECT * FROM t_inventory WHERE warehouse_id = #{warehouseId} AND deleted = 0")
    List<Inventory> findByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * CAS更新库存（预留：available减少，reserved增加）
     */
    @Update("UPDATE t_inventory SET " +
            "available_qty = available_qty - #{qty}, " +
            "reserved_qty = reserved_qty + #{qty}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} " +
            "AND version = #{version} AND available_qty >= #{qty} AND deleted = 0")
    int casReserve(@Param("skuId") Long skuId, 
                   @Param("warehouseId") Long warehouseId,
                   @Param("qty") int qty, 
                   @Param("version") int version);

    /**
     * CAS更新库存（确认：reserved减少）
     */
    @Update("UPDATE t_inventory SET " +
            "reserved_qty = reserved_qty - #{qty}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} " +
            "AND version = #{version} AND reserved_qty >= #{qty} AND deleted = 0")
    int casConfirm(@Param("skuId") Long skuId, 
                   @Param("warehouseId") Long warehouseId,
                   @Param("qty") int qty, 
                   @Param("version") int version);

    /**
     * CAS更新库存（释放：available增加，reserved减少）
     */
    @Update("UPDATE t_inventory SET " +
            "available_qty = available_qty + #{qty}, " +
            "reserved_qty = reserved_qty - #{qty}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} " +
            "AND version = #{version} AND reserved_qty >= #{qty} AND deleted = 0")
    int casRelease(@Param("skuId") Long skuId, 
                   @Param("warehouseId") Long warehouseId,
                   @Param("qty") int qty, 
                   @Param("version") int version);

    /**
     * 直接更新可用库存（用于同步场景）
     */
    @Update("UPDATE t_inventory SET " +
            "available_qty = #{availableQty}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND deleted = 0")
    int updateAvailableQty(@Param("skuId") Long skuId, 
                           @Param("warehouseId") Long warehouseId,
                           @Param("availableQty") int availableQty);

    /**
     * CAS更新库存（退款回补：available增加）
     */
    @Update("UPDATE t_inventory SET " +
            "available_qty = available_qty + #{qty}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} " +
            "AND version = #{version} AND deleted = 0")
    int casRefundRestore(@Param("skuId") Long skuId, 
                         @Param("warehouseId") Long warehouseId,
                         @Param("qty") int qty, 
                         @Param("version") int version);
}
