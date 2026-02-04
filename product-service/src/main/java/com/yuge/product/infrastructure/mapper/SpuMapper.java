package com.yuge.product.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.product.domain.entity.Spu;
import org.apache.ibatis.annotations.Mapper;

/**
 * SPU Mapper接口
 */
@Mapper
public interface SpuMapper extends BaseMapper<Spu> {
}
