package com.yuge.payment.infrastructure.scheduler;

import com.yuge.payment.domain.entity.PayOrder;
import com.yuge.payment.domain.enums.PayStatus;
import com.yuge.payment.domain.enums.ReconcileAction;
import com.yuge.payment.infrastructure.repository.PayOrderRepository;
import com.yuge.payment.infrastructure.repository.PayReconcileAuditRepository;
import com.yuge.payment.application.PaymentService;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 支付补单/对账定时任务
 * 
 * 功能：
 * 1. 扫描超时的PAYING状态支付单，模拟"主动查单"
 * 2. 根据查单结果补推进或关闭支付单
 * 3. 记录对账审计日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileScheduler {

    private final PayOrderRepository payOrderRepository;
    private final PayReconcileAuditRepository payReconcileAuditRepository;
    private final PaymentService paymentService;

    @Value("${payment.reconcile.timeout-threshold-minutes:5}")
    private int timeoutThresholdMinutes;

    @Value("${payment.reconcile.batch-size:100}")
    private int batchSize;

    /**
     * 定时扫描超时的PAYING状态支付单
     * 默认每60秒执行一次
     */
    @Scheduled(fixedDelayString = "${payment.reconcile.scan-interval:60}000")
    public void scanTimeoutPayingOrders() {
        // 设置traceId
        TraceContext.setTraceId("RECONCILE-" + UUID.randomUUID().toString().substring(0, 8));

        try {
            log.info("[ReconcileScheduler] Starting to scan timeout PAYING orders...");

            LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutThresholdMinutes);
            List<PayOrder> timeoutOrders = payOrderRepository.findTimeoutPayingOrders(timeoutThreshold, batchSize);

            if (timeoutOrders.isEmpty()) {
                log.debug("[ReconcileScheduler] No timeout PAYING orders found");
                return;
            }

            log.info("[ReconcileScheduler] Found {} timeout PAYING orders", timeoutOrders.size());

            for (PayOrder payOrder : timeoutOrders) {
                try {
                    processTimeoutPayingOrder(payOrder);
                } catch (Exception e) {
                    log.error("[ReconcileScheduler] Failed to process timeout order, payNo={}, error={}",
                            payOrder.getPayNo(), e.getMessage(), e);
                }
            }

            log.info("[ReconcileScheduler] Finished scanning timeout PAYING orders");

        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 定时扫描过期的INIT状态支付单（关闭）
     * 默认每120秒执行一次
     */
    @Scheduled(fixedDelayString = "${payment.reconcile.expire-scan-interval:120}000")
    public void scanExpiredInitOrders() {
        TraceContext.setTraceId("EXPIRE-" + UUID.randomUUID().toString().substring(0, 8));

        try {
            log.info("[ReconcileScheduler] Starting to scan expired INIT orders...");

            LocalDateTime now = LocalDateTime.now();
            List<PayOrder> expiredOrders = payOrderRepository.findExpiredInitOrders(now, batchSize);

            if (expiredOrders.isEmpty()) {
                log.debug("[ReconcileScheduler] No expired INIT orders found");
                return;
            }

            log.info("[ReconcileScheduler] Found {} expired INIT orders", expiredOrders.size());

            for (PayOrder payOrder : expiredOrders) {
                try {
                    closeExpiredOrder(payOrder);
                } catch (Exception e) {
                    log.error("[ReconcileScheduler] Failed to close expired order, payNo={}, error={}",
                            payOrder.getPayNo(), e.getMessage(), e);
                }
            }

            log.info("[ReconcileScheduler] Finished scanning expired INIT orders");

        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 处理超时的PAYING状态支付单
     * 模拟主动查单，根据结果补推进或关闭
     */
    private void processTimeoutPayingOrder(PayOrder payOrder) {
        String payNo = payOrder.getPayNo();
        String orderNo = payOrder.getOrderNo();
        String beforeStatus = payOrder.getStatus();

        log.info("[ReconcileScheduler] Processing timeout PAYING order, payNo={}, orderNo={}", payNo, orderNo);

        // 1. 模拟主动查单（实际场景中调用第三方支付平台查询接口）
        MockQueryResult queryResult = mockQueryPaymentStatus(payOrder);

        // 2. 记录查单审计
        payReconcileAuditRepository.saveAudit(
                payNo, orderNo,
                ReconcileAction.QUERY.getCode(),
                beforeStatus,
                null,
                queryResult.name(),
                "主动查单: " + queryResult.getDesc()
        );

        // 3. 根据查单结果处理
        switch (queryResult) {
            case SUCCESS:
                // 支付成功，补推进状态
                handleQuerySuccess(payOrder);
                break;
            case FAILED:
                // 支付失败，更新状态
                handleQueryFailed(payOrder);
                break;
            case PAYING:
                // 仍在支付中，检查是否超过最大等待时间
                handleQueryPaying(payOrder);
                break;
            case NOT_FOUND:
                // 渠道无此订单，关闭支付单
                handleQueryNotFound(payOrder);
                break;
        }
    }

    /**
     * 模拟查询支付状态
     * 实际场景中应调用第三方支付平台的查询接口
     */
    private MockQueryResult mockQueryPaymentStatus(PayOrder payOrder) {
        // 模拟逻辑：
        // - 创建超过10分钟的订单，50%概率返回SUCCESS，30%返回FAILED，20%返回NOT_FOUND
        // - 创建5-10分钟的订单，返回PAYING
        // - 其他情况返回PAYING

        LocalDateTime createdAt = payOrder.getCreatedAt();
        long minutesSinceCreation = java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();

        if (minutesSinceCreation > 10) {
            double random = Math.random();
            if (random < 0.5) {
                return MockQueryResult.SUCCESS;
            } else if (random < 0.8) {
                return MockQueryResult.FAILED;
            } else {
                return MockQueryResult.NOT_FOUND;
            }
        }

        return MockQueryResult.PAYING;
    }

    /**
     * 处理查单结果为SUCCESS
     */
    private void handleQuerySuccess(PayOrder payOrder) {
        String payNo = payOrder.getPayNo();
        String mockChannelTradeNo = "MOCK_" + System.currentTimeMillis();

        boolean updated = payOrderRepository.casUpdateToSuccess(payNo, mockChannelTradeNo, LocalDateTime.now());

        if (updated) {
            log.info("[ReconcileScheduler] Order updated to SUCCESS via reconcile, payNo={}", payNo);

            // 记录审计
            payReconcileAuditRepository.saveAudit(
                    payNo, payOrder.getOrderNo(),
                    ReconcileAction.NOTIFY.getCode(),
                    PayStatus.PAYING.getCode(),
                    PayStatus.SUCCESS.getCode(),
                    "SUCCESS",
                    "补单成功，状态更新为SUCCESS"
            );

            // 补发支付成功事件
            paymentService.publishPaymentSucceededEvent(payOrder, mockChannelTradeNo);
        } else {
            log.warn("[ReconcileScheduler] CAS update to SUCCESS failed, payNo={}", payNo);
        }
    }

    /**
     * 处理查单结果为FAILED
     */
    private void handleQueryFailed(PayOrder payOrder) {
        String payNo = payOrder.getPayNo();

        boolean updated = payOrderRepository.casUpdateToFailed(payNo);

        if (updated) {
            log.info("[ReconcileScheduler] Order updated to FAILED via reconcile, payNo={}", payNo);

            payReconcileAuditRepository.saveAudit(
                    payNo, payOrder.getOrderNo(),
                    ReconcileAction.NOTIFY.getCode(),
                    PayStatus.PAYING.getCode(),
                    PayStatus.FAILED.getCode(),
                    "FAILED",
                    "补单：渠道返回支付失败"
            );
        }
    }

    /**
     * 处理查单结果为PAYING（仍在支付中）
     */
    private void handleQueryPaying(PayOrder payOrder) {
        // 检查是否超过支付过期时间
        if (payOrder.getExpireAt() != null && LocalDateTime.now().isAfter(payOrder.getExpireAt())) {
            // 已过期，关闭支付单
            closeExpiredOrder(payOrder);
        } else {
            // 未过期，等待下次扫描
            log.info("[ReconcileScheduler] Order still PAYING, will retry later, payNo={}", payOrder.getPayNo());

            payReconcileAuditRepository.saveAudit(
                    payOrder.getPayNo(), payOrder.getOrderNo(),
                    ReconcileAction.QUERY.getCode(),
                    PayStatus.PAYING.getCode(),
                    null,
                    "PAYING",
                    "渠道返回仍在支付中，等待下次扫描"
            );
        }
    }

    /**
     * 处理查单结果为NOT_FOUND
     */
    private void handleQueryNotFound(PayOrder payOrder) {
        String payNo = payOrder.getPayNo();

        boolean closed = payOrderRepository.casClose(payNo, "渠道无此订单记录");

        if (closed) {
            log.info("[ReconcileScheduler] Order closed (NOT_FOUND), payNo={}", payNo);

            payReconcileAuditRepository.saveAudit(
                    payNo, payOrder.getOrderNo(),
                    ReconcileAction.CLOSE.getCode(),
                    PayStatus.PAYING.getCode(),
                    PayStatus.CLOSED.getCode(),
                    "NOT_FOUND",
                    "渠道无此订单记录，关闭支付单"
            );
        }
    }

    /**
     * 关闭过期的支付单
     */
    private void closeExpiredOrder(PayOrder payOrder) {
        String payNo = payOrder.getPayNo();
        String beforeStatus = payOrder.getStatus();

        boolean closed = payOrderRepository.casClose(payNo, "支付超时，自动关闭");

        if (closed) {
            log.info("[ReconcileScheduler] Expired order closed, payNo={}", payNo);

            payReconcileAuditRepository.saveAudit(
                    payNo, payOrder.getOrderNo(),
                    ReconcileAction.CLOSE.getCode(),
                    beforeStatus,
                    PayStatus.CLOSED.getCode(),
                    null,
                    "支付超时，自动关闭"
            );
        }
    }

    /**
     * 模拟查单结果枚举
     */
    private enum MockQueryResult {
        SUCCESS("支付成功"),
        FAILED("支付失败"),
        PAYING("支付中"),
        NOT_FOUND("订单不存在");

        private final String desc;

        MockQueryResult(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }
}
