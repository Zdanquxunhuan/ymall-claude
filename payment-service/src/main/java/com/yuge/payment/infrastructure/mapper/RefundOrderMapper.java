package com.yuge.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.payment.domain.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 退款单Mapper
 */
@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    /**
     * CAS更新为退款中
     */
    @Update("UPDATE t_refund_order SET status = 'REFUNDING', version = version + 1, updated_at = NOW() " +
            "WHERE refund_no = #{refundNo} AND status = 'INIT' AND deleted = 0")
    int casUpdateToRefunding(@Param("refundNo") String refundNo);

    /**
     * CAS更新为退款成功
     */
    @Update("UPDATE t_refund_order SET status = 'SUCCESS', channel_refund_no = #{channelRefundNo}, " +
            "refunded_at = NOW(), version = version + 1, updated_at = NOW() " +
            "WHERE refund_no = #{refundNo} AND status IN ('INIT', 'REFUNDING') AND deleted = 0")
    int casUpdateToSuccess(@Param("refundNo") String refundNo, @Param("channelRefundNo") String channelRefundNo);

    /**
     * CAS更新为退款失败
     */
    @Update("UPDATE t_refund_order SET status = 'FAILED', version = version + 1, updated_at = NOW() " +
            "WHERE refund_no = #{refundNo} AND status IN ('INIT', 'REFUNDING') AND deleted = 0")
    int casUpdateToFailed(@Param("refundNo") String refundNo);
}
