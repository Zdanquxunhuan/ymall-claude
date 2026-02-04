package com.yuge.product.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.product.domain.entity.Sku;
import org.apache.ibatis.annotations.Mapper;

/**
 * SKU Mapper接口
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {
}
