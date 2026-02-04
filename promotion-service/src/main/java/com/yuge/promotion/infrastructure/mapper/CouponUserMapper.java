package com.yuge.promotion.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.promotion.domain.entity.CouponUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户优惠券Mapper
 */
@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUser> {

    /**
     * CAS更新状态（核销）
     */
    @Update("UPDATE t_coupon_user SET status = #{toStatus}, " +
            "used_time = NOW(), used_order_no = #{orderNo}, " +
            "discount_amount = #{discountAmount}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{fromStatus} AND version = #{version}")
    int casUpdateStatusForUse(@Param("id") Long id,
                              @Param("fromStatus") String fromStatus,
                              @Param("toStatus") String toStatus,
                              @Param("orderNo") String orderNo,
                              @Param("discountAmount") java.math.BigDecimal discountAmount,
                              @Param("version") Integer version);

    /**
     * CAS锁定优惠券（试算锁定）
     */
    @Update("UPDATE t_coupon_user SET status = 'LOCKED', " +
            "locked_time = NOW(), lock_expire_time = #{lockExpireTime}, " +
            "price_lock_no = #{priceLockNo}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'AVAILABLE' AND version = #{version}")
    int casLockCoupon(@Param("id") Long id,
                      @Param("priceLockNo") String priceLockNo,
                      @Param("lockExpireTime") java.time.LocalDateTime lockExpireTime,
                      @Param("version") Integer version);

    /**
     * CAS解锁优惠券
     */
    @Update("UPDATE t_coupon_user SET status = 'AVAILABLE', " +
            "locked_time = NULL, lock_expire_time = NULL, " +
            "price_lock_no = NULL, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'LOCKED' AND price_lock_no = #{priceLockNo}")
    int casUnlockCoupon(@Param("id") Long id, @Param("priceLockNo") String priceLockNo);

    /**
     * 释放过期锁定的优惠券
     */
    @Update("UPDATE t_coupon_user SET status = 'AVAILABLE', " +
            "locked_time = NULL, lock_expire_time = NULL, " +
            "price_lock_no = NULL, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE status = 'LOCKED' AND lock_expire_time < NOW()")
    int releaseExpiredLocks();
}
