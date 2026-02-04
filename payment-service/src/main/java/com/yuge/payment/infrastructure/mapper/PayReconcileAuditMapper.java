package com.yuge.payment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.payment.domain.entity.PayReconcileAudit;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付对账审计Mapper
 */
@Mapper
public interface PayReconcileAuditMapper extends BaseMapper<PayReconcileAudit> {
}
