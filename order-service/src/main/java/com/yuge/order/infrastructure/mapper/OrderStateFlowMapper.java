package com.yuge.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.order.domain.entity.OrderStateFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单状态流转Mapper
 */
@Mapper
public interface OrderStateFlowMapper extends BaseMapper<OrderStateFlow> {

    /**
     * 根据订单号查询状态流转记录
     */
    @Select("SELECT * FROM t_order_state_flow WHERE order_no = #{orderNo} ORDER BY created_at ASC")
    List<OrderStateFlow> selectByOrderNo(@Param("orderNo") String orderNo);
}
