package com.yuge.promotion.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.promotion.domain.entity.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券Mapper
 */
@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * CAS增加已发放数量
     */
    @Update("UPDATE t_coupon SET issued_quantity = issued_quantity + #{count}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{couponId} AND version = #{version} " +
            "AND issued_quantity + #{count} <= total_quantity")
    int casIncrementIssuedQuantity(@Param("couponId") Long couponId,
                                   @Param("count") Integer count,
                                   @Param("version") Integer version);
}
