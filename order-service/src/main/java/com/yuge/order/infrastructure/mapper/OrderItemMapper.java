package com.yuge.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.order.domain.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单明细Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    /**
     * 根据订单号查询订单明细
     */
    @Select("SELECT * FROM t_order_item WHERE order_no = #{orderNo}")
    List<OrderItem> selectByOrderNo(@Param("orderNo") String orderNo);
}
