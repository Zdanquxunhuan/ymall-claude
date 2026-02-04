package com.yuge.promotion.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.promotion.domain.entity.Coupon;
import com.yuge.promotion.infrastructure.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 优惠券仓储
 */
@Repository
@RequiredArgsConstructor
public class CouponRepository {

    private final CouponMapper couponMapper;

    public void save(Coupon coupon) {
        if (coupon.getId() == null) {
            couponMapper.insert(coupon);
        } else {
            couponMapper.updateById(coupon);
        }
    }

    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(couponMapper.selectById(id));
    }

    public Optional<Coupon> findByCode(String couponCode) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getCouponCode, couponCode);
        return Optional.ofNullable(couponMapper.selectOne(wrapper));
    }

    public List<Coupon> findByStatus(String status) {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, status);
        return couponMapper.selectList(wrapper);
    }

    public List<Coupon> findActiveCoupons() {
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, "ACTIVE");
        return couponMapper.selectList(wrapper);
    }

    public boolean casIncrementIssuedQuantity(Long couponId, Integer count, Integer version) {
        return couponMapper.casIncrementIssuedQuantity(couponId, count, version) > 0;
    }
}
