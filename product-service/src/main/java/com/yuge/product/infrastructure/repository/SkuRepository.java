package com.yuge.product.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.product.domain.entity.Sku;
import com.yuge.product.infrastructure.mapper.SkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SKU Repository
 */
@Repository
@RequiredArgsConstructor
public class SkuRepository {

    private final SkuMapper skuMapper;

    /**
     * 保存SKU
     */
    public void save(Sku sku) {
        if (sku.getSkuId() == null) {
            skuMapper.insert(sku);
        } else {
            skuMapper.updateById(sku);
        }
    }

    /**
     * 根据ID查询
     */
    public Optional<Sku> findById(Long skuId) {
        return Optional.ofNullable(skuMapper.selectById(skuId));
    }

    /**
     * 根据SPU ID查询
     */
    public List<Sku> findBySpuId(Long spuId) {
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getSpuId, spuId);
        return skuMapper.selectList(wrapper);
    }

    /**
     * 根据状态查询
     */
    public List<Sku> findByStatus(String status) {
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getStatus, status);
        return skuMapper.selectList(wrapper);
    }

    /**
     * 根据SKU编码查询
     */
    public Optional<Sku> findBySkuCode(String skuCode) {
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sku::getSkuCode, skuCode);
        return Optional.ofNullable(skuMapper.selectOne(wrapper));
    }
}
