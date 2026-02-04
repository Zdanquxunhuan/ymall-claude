package com.yuge.promotion.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.promotion.domain.entity.CouponUser;
import com.yuge.promotion.infrastructure.mapper.CouponUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户优惠券仓储
 */
@Repository
@RequiredArgsConstructor
public class CouponUserRepository {

    private final CouponUserMapper couponUserMapper;

    public void save(CouponUser couponUser) {
        if (couponUser.getId() == null) {
            couponUserMapper.insert(couponUser);
        } else {
            couponUserMapper.updateById(couponUser);
        }
    }

    public Optional<CouponUser> findById(Long id) {
        return Optional.ofNullable(couponUserMapper.selectById(id));
    }

    public Optional<CouponUser> findByUserCouponNo(String userCouponNo) {
        LambdaQueryWrapper<CouponUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUser::getUserCouponNo, userCouponNo);
        return Optional.ofNullable(couponUserMapper.selectOne(wrapper));
    }

    public Optional<CouponUser> findByUserIdAndReceiveRequestId(Long userId, String receiveRequestId) {
        LambdaQueryWrapper<CouponUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUser::getUserId, userId)
               .eq(CouponUser::getReceiveRequestId, receiveRequestId);
        return Optional.ofNullable(couponUserMapper.selectOne(wrapper));
    }

    public List<CouponUser> findByUserIdAndStatus(Long userId, String status) {
        LambdaQueryWrapper<CouponUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUser::getUserId, userId)
               .eq(CouponUser::getStatus, status);
        return couponUserMapper.selectList(wrapper);
    }

    public List<CouponUser> findAvailableByUserId(Long userId) {
        LambdaQueryWrapper<CouponUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUser::getUserId, userId)
               .eq(CouponUser::getStatus, "AVAILABLE")
               .le(CouponUser::getValidStartTime, LocalDateTime.now())
               .ge(CouponUser::getValidEndTime, LocalDateTime.now());
        return couponUserMapper.selectList(wrapper);
    }

    public int countByUserIdAndCouponId(Long userId, Long couponId) {
        LambdaQueryWrapper<CouponUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CouponUser::getUserId, userId)
               .eq(CouponUser::getCouponId, couponId);
        return Math.toIntExact(couponUserMapper.selectCount(wrapper));
    }

    public boolean casUpdateStatusForUse(Long id, String fromStatus, String toStatus,
                                         String orderNo, BigDecimal discountAmount, Integer version) {
        return couponUserMapper.casUpdateStatusForUse(id, fromStatus, toStatus, orderNo, discountAmount, version) > 0;
    }

    public boolean casLockCoupon(Long id, String priceLockNo, LocalDateTime lockExpireTime, Integer version) {
        return couponUserMapper.casLockCoupon(id, priceLockNo, lockExpireTime, version) > 0;
    }

    public boolean casUnlockCoupon(Long id, String priceLockNo) {
        return couponUserMapper.casUnlockCoupon(id, priceLockNo) > 0;
    }

    public int releaseExpiredLocks() {
        return couponUserMapper.releaseExpiredLocks();
    }
}
