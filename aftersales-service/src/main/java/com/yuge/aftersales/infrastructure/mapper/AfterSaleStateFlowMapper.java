package com.yuge.aftersales.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.aftersales.domain.entity.AfterSaleStateFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后状态流转Mapper
 */
@Mapper
public interface AfterSaleStateFlowMapper extends BaseMapper<AfterSaleStateFlow> {
}
