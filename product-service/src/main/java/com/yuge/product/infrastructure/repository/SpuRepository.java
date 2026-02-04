package com.yuge.product.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.product.domain.entity.Spu;
import com.yuge.product.infrastructure.mapper.SpuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * SPU Repository
 */
@Repository
@RequiredArgsConstructor
public class SpuRepository {

    private final SpuMapper spuMapper;

    /**
     * 保存SPU
     */
    public void save(Spu spu) {
        if (spu.getSpuId() == null) {
            spuMapper.insert(spu);
        } else {
            spuMapper.updateById(spu);
        }
    }

    /**
     * 根据ID查询
     */
    public Optional<Spu> findById(Long spuId) {
        return Optional.ofNullable(spuMapper.selectById(spuId));
    }

    /**
     * 根据分类ID查询
     */
    public List<Spu> findByCategoryId(Long categoryId) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getCategoryId, categoryId);
        return spuMapper.selectList(wrapper);
    }

    /**
     * 根据状态查询
     */
    public List<Spu> findByStatus(String status) {
        LambdaQueryWrapper<Spu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Spu::getStatus, status);
        return spuMapper.selectList(wrapper);
    }
}
