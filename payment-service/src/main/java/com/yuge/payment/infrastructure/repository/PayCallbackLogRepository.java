package com.yuge.payment.infrastructure.repository;

import com.yuge.payment.domain.entity.PayCallbackLog;
import com.yuge.payment.infrastructure.mapper.PayCallbackLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 支付回调日志仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PayCallbackLogRepository {

    private final PayCallbackLogMapper payCallbackLogMapper;

    public void save(PayCallbackLog callbackLog) {
        payCallbackLogMapper.insert(callbackLog);
    }

    public Optional<PayCallbackLog> findByCallbackId(String callbackId) {
        return payCallbackLogMapper.selectByCallbackId(callbackId);
    }

    public Optional<PayCallbackLog> findLatestByPayNo(String payNo) {
        return payCallbackLogMapper.selectLatestByPayNo(payNo);
    }
}
