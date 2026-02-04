package com.yuge.fulfillment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.fulfillment.domain.entity.Waybill;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运单Mapper
 */
@Mapper
public interface WaybillMapper extends BaseMapper<Waybill> {
}
