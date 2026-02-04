package com.yuge.product.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.product.domain.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * MQ消费日志 Mapper接口
 */
@Mapper
public interface MqConsumeLogMapper extends BaseMapper<MqConsumeLog> {
}
