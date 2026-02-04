package com.yuge.pricing.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.pricing.domain.entity.PriceLock;
import com.yuge.pricing.infrastructure.mapper.PriceLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 价格锁仓储
 */
@Repository
@RequiredArgsConstructor
public class PriceLockRepository {

    private final PriceLockMapper priceLockMapper;

    public void save(PriceLock priceLock) {
        if (priceLock.getId() == null) {
            priceLockMapper.insert(priceLock);
        } else {
            priceLockMapper.updateById(priceLock);
        }
    }

    public Optional<PriceLock> findById(Long id) {
        return Optional.ofNullable(priceLockMapper.selectById(id));
    }

    public Optional<PriceLock> findByPriceLockNo(String priceLockNo) {
        LambdaQueryWrapper<PriceLock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PriceLock::getPriceLockNo, priceLockNo);
        return Optional.ofNullable(priceLockMapper.selectOne(wrapper));
    }

    public boolean casUse(Long id, String orderNo, Integer version) {
        return priceLockMapper.casUse(id, orderNo, version) > 0;
    }

    public boolean casCancel(Long id, Integer version) {
        return priceLockMapper.casCancel(id, version) > 0;
    }

    public int expireLocks() {
        return priceLockMapper.expireLocks();
    }
}
