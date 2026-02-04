package com.yuge.fulfillment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.fulfillment.domain.entity.Shipment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 发货单Mapper
 */
@Mapper
public interface ShipmentMapper extends BaseMapper<Shipment> {

    /**
     * CAS更新发货单状态
     *
     * @param shipmentNo     发货单号
     * @param expectedStatus 期望状态
     * @param newStatus      新状态
     * @return 更新行数
     */
    @Update("UPDATE t_shipment SET status = #{newStatus}, updated_at = NOW(), version = version + 1 " +
            "WHERE shipment_no = #{shipmentNo} AND status = #{expectedStatus} AND deleted = 0")
    int casUpdateStatus(@Param("shipmentNo") String shipmentNo,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("newStatus") String newStatus);

    /**
     * CAS更新发货单状态为已发货
     *
     * @param shipmentNo     发货单号
     * @param expectedStatus 期望状态
     * @param newStatus      新状态
     * @return 更新行数
     */
    @Update("UPDATE t_shipment SET status = #{newStatus}, shipped_at = NOW(), updated_at = NOW(), version = version + 1 " +
            "WHERE shipment_no = #{shipmentNo} AND status = #{expectedStatus} AND deleted = 0")
    int casUpdateStatusToShipped(@Param("shipmentNo") String shipmentNo,
                                  @Param("expectedStatus") String expectedStatus,
                                  @Param("newStatus") String newStatus);

    /**
     * CAS更新发货单状态为已签收
     *
     * @param shipmentNo     发货单号
     * @param expectedStatus 期望状态
     * @param newStatus      新状态
     * @return 更新行数
     */
    @Update("UPDATE t_shipment SET status = #{newStatus}, delivered_at = NOW(), updated_at = NOW(), version = version + 1 " +
            "WHERE shipment_no = #{shipmentNo} AND status = #{expectedStatus} AND deleted = 0")
    int casUpdateStatusToDelivered(@Param("shipmentNo") String shipmentNo,
                                    @Param("expectedStatus") String expectedStatus,
                                    @Param("newStatus") String newStatus);
}
