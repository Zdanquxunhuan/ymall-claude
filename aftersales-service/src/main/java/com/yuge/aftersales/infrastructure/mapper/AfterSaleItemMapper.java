package com.yuge.aftersales.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.aftersales.domain.entity.AfterSaleItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后单明细Mapper
 */
@Mapper
public interface AfterSaleItemMapper extends BaseMapper<AfterSaleItem> {
}
