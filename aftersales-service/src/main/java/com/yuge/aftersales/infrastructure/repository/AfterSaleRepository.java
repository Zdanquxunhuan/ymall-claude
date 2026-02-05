package com.yuge.aftersales.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.aftersales.domain.entity.AfterSale;
import com.yuge.aftersales.domain.enums.AfterSaleStatus;
import com.yuge.aftersales.infrastructure.mapper.AfterSaleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 售后单仓储
 */
@Repository
@RequiredArgsConstructor
public class AfterSaleRepository {

    private final AfterSaleMapper afterSaleMapper;

    public void save(AfterSale afterSale) {
        afterSaleMapper.insert(afterSale);
    }

    public Optional<AfterSale> findByAsNo(String asNo) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getAsNo, asNo);
        return Optional.ofNullable(afterSaleMapper.selectOne(wrapper));
    }

    public Optional<AfterSale> findByOrderNo(String orderNo) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getOrderNo, orderNo);
        wrapper.orderByDesc(AfterSale::getCreatedAt);
        wrapper.last("LIMIT 1");
        return Optional.ofNullable(afterSaleMapper.selectOne(wrapper));
    }

    public List<AfterSale> findByOrderNoAndStatus(String orderNo, AfterSaleStatus status) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getOrderNo, orderNo);
        wrapper.eq(AfterSale::getStatus, status.getCode());
        return afterSaleMapper.selectList(wrapper);
    }

    public List<AfterSale> findByUserId(Long userId) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getUserId, userId);
        wrapper.orderByDesc(AfterSale::getCreatedAt);
        return afterSaleMapper.selectList(wrapper);
    }

    public List<AfterSale> findByStatus(AfterSaleStatus status) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getStatus, status.getCode());
        return afterSaleMapper.selectList(wrapper);
    }

    public Optional<AfterSale> findByRefundNo(String refundNo) {
        LambdaQueryWrapper<AfterSale> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AfterSale::getRefundNo, refundNo);
        return Optional.ofNullable(afterSaleMapper.selectOne(wrapper));
    }

    public boolean casUpdateStatus(String asNo, AfterSaleStatus fromStatus, 
                                   AfterSaleStatus toStatus, Integer version) {
        return afterSaleMapper.casUpdateStatus(asNo, fromStatus.getCode(), 
                toStatus.getCode(), version) > 0;
    }

    public boolean casApprove(String asNo, String approvedBy, Integer version) {
        return afterSaleMapper.casApprove(asNo, approvedBy, version) > 0;
    }

    public boolean casReject(String asNo, String rejectReason, String approvedBy, Integer version) {
        return afterSaleMapper.casReject(asNo, rejectReason, approvedBy, version) > 0;
    }

    public boolean casStartRefund(String asNo, String refundNo, Integer version) {
        return afterSaleMapper.casStartRefund(asNo, refundNo, version) > 0;
    }

    public boolean casRefunded(String asNo) {
        return afterSaleMapper.casRefunded(asNo) > 0;
    }

    public boolean casCancel(String asNo, Integer version) {
        return afterSaleMapper.casCancel(asNo, version) > 0;
    }
}
