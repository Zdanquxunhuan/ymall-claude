package com.yuge.aftersales.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.aftersales.domain.entity.AfterSaleItem;
import com.yuge.aftersales.infrastructure.mapper.AfterSaleItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 售后单明细仓储
 */
@Repository
@RequiredArgsConstructor
public class AfterSaleItemRepository {

    private final AfterSaleItemMapper afterSaleItemMapper;

    public void save(AfterSaleItem item) {
        afterSaleItemMapper.insert(item);
    }

    public void saveBatch(List<AfterSaleItem> items) {
        for (AfterSaleItem item : items) {
            afterSaleItemMapper.insert(item);
        }
    }

    public List<AfterSaleItem> findByAsNo(String asNo) {
        LambdaQueryWrapper<AfterSaleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSaleItem::getAsNo, asNo);
        return afterSaleItemMapper.selectList(wrapper);
    }

    public List<AfterSaleItem> findByOrderNo(String orderNo) {
        LambdaQueryWrapper<AfterSaleItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSaleItem::getOrderNo, orderNo);
        return afterSaleItemMapper.selectList(wrapper);
    }
}
