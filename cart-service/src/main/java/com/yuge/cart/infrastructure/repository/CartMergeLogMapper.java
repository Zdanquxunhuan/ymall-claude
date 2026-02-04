package com.yuge.cart.infrastructure.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.cart.domain.entity.CartMergeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车合并日志Mapper
 */
@Mapper
public interface CartMergeLogMapper extends BaseMapper<CartMergeLog> {
}
