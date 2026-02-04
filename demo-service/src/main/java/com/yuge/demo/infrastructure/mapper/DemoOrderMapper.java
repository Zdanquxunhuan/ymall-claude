package com.yuge.demo.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.demo.domain.entity.DemoOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 演示订单Mapper
 */
@Mapper
public interface DemoOrderMapper extends BaseMapper<DemoOrder> {

    /**
     * 根据订单号查询订单
     */
    @Select("SELECT * FROM t_demo_order WHERE order_no = #{orderNo} AND deleted = 0")
    DemoOrder selectByOrderNo(@Param("orderNo") String orderNo);
}
