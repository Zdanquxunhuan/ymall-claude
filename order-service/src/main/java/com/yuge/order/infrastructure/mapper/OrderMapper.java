package com.yuge.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 订单Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 根据订单号查询订单
     */
    @Select("SELECT * FROM t_order WHERE order_no = #{orderNo} AND deleted = 0")
    Order selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据用户ID和客户端请求ID查询订单（幂等查询）
     */
    @Select("SELECT * FROM t_order WHERE user_id = #{userId} AND client_request_id = #{clientRequestId} AND deleted = 0")
    Order selectByUserIdAndClientRequestId(@Param("userId") Long userId, @Param("clientRequestId") String clientRequestId);

    /**
     * CAS更新订单状态
     * 
     * @param orderNo 订单号
     * @param fromStatus 原状态
     * @param toStatus 目标状态
     * @param version 当前版本号
     * @return 更新行数
     */
    @Update("UPDATE t_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() " +
            "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int casUpdateStatus(@Param("orderNo") String orderNo, 
                        @Param("fromStatus") String fromStatus, 
                        @Param("toStatus") String toStatus,
                        @Param("version") Integer version);

    /**
     * CAS更新订单状态（仅基于状态，不检查版本号）
     * 用于事件驱动的状态更新，防止乱序
     * 
     * @param orderNo 订单号
     * @param fromStatus 原状态
     * @param toStatus 目标状态
     * @return 更新行数
     */
    @Update("UPDATE t_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() " +
            "WHERE order_no = #{orderNo} AND status = #{fromStatus} AND deleted = 0")
    int casUpdateStatusOnly(@Param("orderNo") String orderNo, 
                            @Param("fromStatus") String fromStatus, 
                            @Param("toStatus") String toStatus);
}
