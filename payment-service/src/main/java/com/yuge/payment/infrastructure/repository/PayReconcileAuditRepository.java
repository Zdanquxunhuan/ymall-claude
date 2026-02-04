package com.yuge.payment.infrastructure.repository;

import com.yuge.payment.domain.entity.PayReconcileAudit;
import com.yuge.payment.infrastructure.mapper.PayReconcileAuditMapper;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 支付对账审计仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PayReconcileAuditRepository {

    private final PayReconcileAuditMapper payReconcileAuditMapper;

    public void save(PayReconcileAudit audit) {
        audit.setTraceId(TraceContext.getTraceId());
        payReconcileAuditMapper.insert(audit);
    }

    /**
     * 记录对账审计
     */
    public void saveAudit(String payNo, String orderNo, String action, 
                          String beforeStatus, String afterStatus, 
                          String queryResult, String remark) {
        PayReconcileAudit audit = new PayReconcileAudit();
        audit.setPayNo(payNo);
        audit.setOrderNo(orderNo);
        audit.setAction(action);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        audit.setQueryResult(queryResult);
        audit.setRemark(remark);
        audit.setTraceId(TraceContext.getTraceId());
        payReconcileAuditMapper.insert(audit);
    }
}
