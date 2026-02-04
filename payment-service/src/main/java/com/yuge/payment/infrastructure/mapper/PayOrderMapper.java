package com.yuge.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.payment.domain.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 支付单Mapper
 */
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 根据订单号查询支付单
     */
    @Select("SELECT * FROM t_pay_order WHERE order_no = #{orderNo} AND deleted = 0")
    Optional<PayOrder> selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据支付单号查询支付单
     */
    @Select("SELECT * FROM t_pay_order WHERE pay_no = #{payNo} AND deleted = 0")
    Optional<PayOrder> selectByPayNo(@Param("payNo") String payNo);

    /**
     * CAS更新支付状态
     */
    @Update("UPDATE t_pay_order SET status = #{toStatus}, version = version + 1, updated_at = NOW() " +
            "WHERE pay_no = #{payNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int casUpdateStatus(@Param("payNo") String payNo,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Integer version);

    /**
     * CAS更新为支付成功
     */
    @Update("UPDATE t_pay_order SET status = 'SUCCESS', channel_trade_no = #{channelTradeNo}, " +
            "paid_at = #{paidAt}, version = version + 1, updated_at = NOW() " +
            "WHERE pay_no = #{payNo} AND status IN ('INIT', 'PAYING') AND deleted = 0")
    int casUpdateToSuccess(@Param("payNo") String payNo,
                           @Param("channelTradeNo") String channelTradeNo,
                           @Param("paidAt") LocalDateTime paidAt);

    /**
     * CAS更新为支付失败
     */
    @Update("UPDATE t_pay_order SET status = 'FAILED', version = version + 1, updated_at = NOW() " +
            "WHERE pay_no = #{payNo} AND status IN ('INIT', 'PAYING') AND deleted = 0")
    int casUpdateToFailed(@Param("payNo") String payNo);

    /**
     * CAS关闭支付单
     */
    @Update("UPDATE t_pay_order SET status = 'CLOSED', close_reason = #{closeReason}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE pay_no = #{payNo} AND status IN ('INIT', 'PAYING') AND deleted = 0")
    int casClose(@Param("payNo") String payNo, @Param("closeReason") String closeReason);

    /**
     * 查询超时的PAYING状态支付单（用于补单）
     */
    @Select("SELECT * FROM t_pay_order WHERE status = 'PAYING' AND created_at < #{timeoutThreshold} " +
            "AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<PayOrder> selectTimeoutPayingOrders(@Param("timeoutThreshold") LocalDateTime timeoutThreshold,
                                              @Param("limit") int limit);

    /**
     * 查询超时的INIT状态支付单（用于关闭）
     */
    @Select("SELECT * FROM t_pay_order WHERE status = 'INIT' AND expire_at < #{now} " +
            "AND deleted = 0 ORDER BY created_at ASC LIMIT #{limit}")
    List<PayOrder> selectExpiredInitOrders(@Param("now") LocalDateTime now,
                                            @Param("limit") int limit);
}
