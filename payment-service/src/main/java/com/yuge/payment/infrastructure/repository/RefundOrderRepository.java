package com.yuge.payment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.payment.domain.entity.RefundOrder;
import com.yuge.payment.infrastructure.mapper.RefundOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 退款单仓储
 */
@Repository
@RequiredArgsConstructor
public class RefundOrderRepository {

    private final RefundOrderMapper refundOrderMapper;

    public void save(RefundOrder refundOrder) {
        refundOrderMapper.insert(refundOrder);
    }

    public Optional<RefundOrder> findByRefundNo(String refundNo) {
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundOrder::getRefundNo, refundNo);
        return Optional.ofNullable(refundOrderMapper.selectOne(wrapper));
    }

    public Optional<RefundOrder> findByOrderNo(String orderNo) {
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundOrder::getOrderNo, orderNo);
        return Optional.ofNullable(refundOrderMapper.selectOne(wrapper));
    }

    public Optional<RefundOrder> findByAsNo(String asNo) {
        LambdaQueryWrapper<RefundOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundOrder::getAsNo, asNo);
        return Optional.ofNullable(refundOrderMapper.selectOne(wrapper));
    }

    public boolean casUpdateToRefunding(String refundNo) {
        return refundOrderMapper.casUpdateToRefunding(refundNo) > 0;
    }

    public boolean casUpdateToSuccess(String refundNo, String channelRefundNo) {
        return refundOrderMapper.casUpdateToSuccess(refundNo, channelRefundNo) > 0;
    }

    public boolean casUpdateToFailed(String refundNo) {
        return refundOrderMapper.casUpdateToFailed(refundNo) > 0;
    }
}
