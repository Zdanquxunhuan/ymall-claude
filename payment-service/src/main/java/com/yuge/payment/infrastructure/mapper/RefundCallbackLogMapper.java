package com.yuge.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.payment.domain.entity.RefundCallbackLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 退款回调日志Mapper
 */
@Mapper
public interface RefundCallbackLogMapper extends BaseMapper<RefundCallbackLog> {
}
