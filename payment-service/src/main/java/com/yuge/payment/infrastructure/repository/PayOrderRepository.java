package com.yuge.payment.infrastructure.repository;

import com.yuge.payment.domain.entity.PayOrder;
import com.yuge.payment.infrastructure.mapper.PayOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 支付单仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PayOrderRepository {

    private final PayOrderMapper payOrderMapper;

    public void save(PayOrder payOrder) {
        payOrderMapper.insert(payOrder);
    }

    public Optional<PayOrder> findByOrderNo(String orderNo) {
        return payOrderMapper.selectByOrderNo(orderNo);
    }

    public Optional<PayOrder> findByPayNo(String payNo) {
        return payOrderMapper.selectByPayNo(payNo);
    }

    /**
     * CAS更新支付状态
     */
    public boolean casUpdateStatus(String payNo, String fromStatus, String toStatus, Integer version) {
        int rows = payOrderMapper.casUpdateStatus(payNo, fromStatus, toStatus, version);
        return rows > 0;
    }

    /**
     * CAS更新为支付成功
     */
    public boolean casUpdateToSuccess(String payNo, String channelTradeNo, LocalDateTime paidAt) {
        int rows = payOrderMapper.casUpdateToSuccess(payNo, channelTradeNo, paidAt);
        return rows > 0;
    }

    /**
     * CAS更新为支付失败
     */
    public boolean casUpdateToFailed(String payNo) {
        int rows = payOrderMapper.casUpdateToFailed(payNo);
        return rows > 0;
    }

    /**
     * CAS关闭支付单
     */
    public boolean casClose(String payNo, String closeReason) {
        int rows = payOrderMapper.casClose(payNo, closeReason);
        return rows > 0;
    }

    /**
     * 查询超时的PAYING状态支付单
     */
    public List<PayOrder> findTimeoutPayingOrders(LocalDateTime timeoutThreshold, int limit) {
        return payOrderMapper.selectTimeoutPayingOrders(timeoutThreshold, limit);
    }

    /**
     * 查询过期的INIT状态支付单
     */
    public List<PayOrder> findExpiredInitOrders(LocalDateTime now, int limit) {
        return payOrderMapper.selectExpiredInitOrders(now, limit);
    }
}
