package com.yuge.aftersales.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.aftersales.domain.entity.AfterSale;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 售后单Mapper
 */
@Mapper
public interface AfterSaleMapper extends BaseMapper<AfterSale> {

    /**
     * CAS更新状态
     */
    @Update("UPDATE t_after_sale SET status = #{toStatus}, version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = #{fromStatus} AND version = #{version} AND deleted = 0")
    int casUpdateStatus(@Param("asNo") String asNo,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Integer version);

    /**
     * CAS更新为已审批
     */
    @Update("UPDATE t_after_sale SET status = 'APPROVED', approved_at = NOW(), approved_by = #{approvedBy}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = 'APPLIED' AND version = #{version} AND deleted = 0")
    int casApprove(@Param("asNo") String asNo,
                   @Param("approvedBy") String approvedBy,
                   @Param("version") Integer version);

    /**
     * CAS更新为已拒绝
     */
    @Update("UPDATE t_after_sale SET status = 'REJECTED', reject_reason = #{rejectReason}, " +
            "approved_at = NOW(), approved_by = #{approvedBy}, version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = 'APPLIED' AND version = #{version} AND deleted = 0")
    int casReject(@Param("asNo") String asNo,
                  @Param("rejectReason") String rejectReason,
                  @Param("approvedBy") String approvedBy,
                  @Param("version") Integer version);

    /**
     * CAS更新为退款中
     */
    @Update("UPDATE t_after_sale SET status = 'REFUNDING', refund_no = #{refundNo}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = 'APPROVED' AND version = #{version} AND deleted = 0")
    int casStartRefund(@Param("asNo") String asNo,
                       @Param("refundNo") String refundNo,
                       @Param("version") Integer version);

    /**
     * CAS更新为已退款
     */
    @Update("UPDATE t_after_sale SET status = 'REFUNDED', refunded_at = NOW(), " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = 'REFUNDING' AND deleted = 0")
    int casRefunded(@Param("asNo") String asNo);

    /**
     * CAS更新为已取消
     */
    @Update("UPDATE t_after_sale SET status = 'CANCELED', version = version + 1, updated_at = NOW() " +
            "WHERE as_no = #{asNo} AND status = 'APPLIED' AND version = #{version} AND deleted = 0")
    int casCancel(@Param("asNo") String asNo, @Param("version") Integer version);
}
