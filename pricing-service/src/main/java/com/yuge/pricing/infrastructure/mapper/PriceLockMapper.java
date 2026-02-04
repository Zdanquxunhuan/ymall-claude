package com.yuge.pricing.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.pricing.domain.entity.PriceLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 价格锁Mapper
 */
@Mapper
public interface PriceLockMapper extends BaseMapper<PriceLock> {

    /**
     * CAS更新状态为已使用
     */
    @Update("UPDATE t_price_lock SET status = 'USED', " +
            "used_at = NOW(), used_order_no = #{orderNo}, " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'LOCKED' AND version = #{version}")
    int casUse(@Param("id") Long id,
               @Param("orderNo") String orderNo,
               @Param("version") Integer version);

    /**
     * CAS取消锁定
     */
    @Update("UPDATE t_price_lock SET status = 'CANCELED', " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE id = #{id} AND status = 'LOCKED' AND version = #{version}")
    int casCancel(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 过期锁定的价格锁
     */
    @Update("UPDATE t_price_lock SET status = 'EXPIRED', " +
            "version = version + 1, updated_at = NOW() " +
            "WHERE status = 'LOCKED' AND expire_at < NOW()")
    int expireLocks();
}
