package com.yuge.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.inventory.domain.entity.InventoryReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 库存预留Mapper
 */
@Mapper
public interface InventoryReservationMapper extends BaseMapper<InventoryReservation> {

    /**
     * 根据订单号、SKU、仓库查询预留记录
     */
    @Select("SELECT * FROM t_inventory_reservation " +
            "WHERE order_no = #{orderNo} AND sku_id = #{skuId} AND warehouse_id = #{warehouseId} AND deleted = 0")
    Optional<InventoryReservation> findByOrderNoAndSkuIdAndWarehouseId(
            @Param("orderNo") String orderNo,
            @Param("skuId") Long skuId,
            @Param("warehouseId") Long warehouseId);

    /**
     * 根据订单号查询所有预留记录
     */
    @Select("SELECT * FROM t_inventory_reservation WHERE order_no = #{orderNo} AND deleted = 0")
    List<InventoryReservation> findByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据订单号和状态查询预留记录
     */
    @Select("SELECT * FROM t_inventory_reservation " +
            "WHERE order_no = #{orderNo} AND status = #{status} AND deleted = 0")
    List<InventoryReservation> findByOrderNoAndStatus(@Param("orderNo") String orderNo, 
                                                       @Param("status") String status);

    /**
     * 查询已过期的预留记录（用于超时释放）
     */
    @Select("SELECT * FROM t_inventory_reservation " +
            "WHERE status = 'RESERVED' AND expire_at < #{now} AND deleted = 0 " +
            "ORDER BY expire_at ASC LIMIT #{limit}")
    List<InventoryReservation> findExpiredReservations(@Param("now") LocalDateTime now, 
                                                        @Param("limit") int limit);

    /**
     * CAS更新预留状态
     */
    @Update("UPDATE t_inventory_reservation SET " +
            "status = #{newStatus}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{oldStatus} AND version = #{version} AND deleted = 0")
    int casUpdateStatus(@Param("id") Long id,
                        @Param("oldStatus") String oldStatus,
                        @Param("newStatus") String newStatus,
                        @Param("version") int version);

    /**
     * 批量更新订单的预留状态
     */
    @Update("UPDATE t_inventory_reservation SET " +
            "status = #{newStatus}, " +
            "version = version + 1, " +
            "updated_at = NOW() " +
            "WHERE order_no = #{orderNo} AND status = #{oldStatus} AND deleted = 0")
    int batchUpdateStatusByOrderNo(@Param("orderNo") String orderNo,
                                    @Param("oldStatus") String oldStatus,
                                    @Param("newStatus") String newStatus);
}
