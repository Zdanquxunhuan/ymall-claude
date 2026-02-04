package com.yuge.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.inventory.domain.entity.InventoryTxn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库存流水Mapper
 */
@Mapper
public interface InventoryTxnMapper extends BaseMapper<InventoryTxn> {

    /**
     * 根据订单号查询流水
     */
    @Select("SELECT * FROM t_inventory_txn WHERE order_no = #{orderNo} ORDER BY created_at DESC")
    List<InventoryTxn> findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据SKU和仓库查询流水
     */
    @Select("SELECT * FROM t_inventory_txn " +
            "WHERE sku_id = #{skuId} AND warehouse_id = #{warehouseId} " +
            "ORDER BY created_at DESC LIMIT #{limit}")
    List<InventoryTxn> findBySkuIdAndWarehouseId(@Param("skuId") Long skuId,
                                                  @Param("warehouseId") Long warehouseId,
                                                  @Param("limit") int limit);

    /**
     * 根据流水ID查询
     */
    @Select("SELECT * FROM t_inventory_txn WHERE txn_id = #{txnId}")
    InventoryTxn findByTxnId(@Param("txnId") String txnId);
}
