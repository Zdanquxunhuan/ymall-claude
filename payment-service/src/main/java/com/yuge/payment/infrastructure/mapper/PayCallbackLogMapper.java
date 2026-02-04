package com.yuge.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.payment.domain.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * 支付回调日志Mapper
 */
@Mapper
public interface PayCallbackLogMapper extends BaseMapper<PayCallbackLog> {

    /**
     * 根据回调ID查询（用于幂等检查）
     */
    @Select("SELECT * FROM t_pay_callback_log WHERE callback_id = #{callbackId}")
    Optional<PayCallbackLog> selectByCallbackId(@Param("callbackId") String callbackId);

    /**
     * 根据支付单号查询最新回调
     */
    @Select("SELECT * FROM t_pay_callback_log WHERE pay_no = #{payNo} ORDER BY created_at DESC LIMIT 1")
    Optional<PayCallbackLog> selectLatestByPayNo(@Param("payNo") String payNo);
}
