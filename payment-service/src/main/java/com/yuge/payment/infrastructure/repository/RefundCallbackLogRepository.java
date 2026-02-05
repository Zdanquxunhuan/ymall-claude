package com.yuge.payment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuge.payment.domain.entity.RefundCallbackLog;
import com.yuge.payment.infrastructure.mapper.RefundCallbackLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 退款回调日志仓储
 */
@Repository
@RequiredArgsConstructor
public class RefundCallbackLogRepository {

    private final RefundCallbackLogMapper refundCallbackLogMapper;

    public void save(RefundCallbackLog log) {
        refundCallbackLogMapper.insert(log);
    }

    public Optional<RefundCallbackLog> findByCallbackId(String callbackId) {
        LambdaQueryWrapper<RefundCallbackLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RefundCallbackLog::getCallbackId, callbackId);
        return Optional.ofNullable(refundCallbackLogMapper.selectOne(wrapper));
    }
}
