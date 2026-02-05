package com.yuge.aftersales.application;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.aftersales.api.dto.*;
import com.yuge.aftersales.domain.entity.AfterSale;
import com.yuge.aftersales.domain.entity.AfterSaleItem;
import com.yuge.aftersales.domain.entity.AfterSaleStateFlow;
import com.yuge.aftersales.domain.enums.AfterSaleStatus;
import com.yuge.aftersales.domain.enums.AfterSaleType;
import com.yuge.aftersales.domain.event.AfterSaleRefundedEvent;
import com.yuge.aftersales.domain.statemachine.AfterSaleStateMachine;
import com.yuge.aftersales.infrastructure.client.PaymentClient;
import com.yuge.aftersales.infrastructure.repository.AfterSaleItemRepository;
import com.yuge.aftersales.infrastructure.repository.AfterSaleRepository;
import com.yuge.aftersales.infrastructure.repository.AfterSaleStateFlowRepository;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.platform.infra.mq.ProducerTemplate;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 售后应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleService {

    private static final String AFTERSALES_TOPIC = "AFTERSALES_TOPIC";
    private static final String TAG_AFTERSALE_REFUNDED = "AFTERSALE_REFUNDED";

    private final AfterSaleRepository afterSaleRepository;
    private final AfterSaleItemRepository afterSaleItemRepository;
    private final AfterSaleStateFlowRepository stateFlowRepository;
    private final PaymentClient paymentClient;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 申请售后
     */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse applyAfterSale(ApplyAfterSaleRequest request) {
        String orderNo = request.getOrderNo();
        log.info("[AfterSaleService] applyAfterSale start, orderNo={}, userId={}", 
                orderNo, request.getUserId());

        // 1. 检查是否已有进行中的售后单
        List<AfterSale> existingAfterSales = afterSaleRepository.findByOrderNoAndStatus(
                orderNo, AfterSaleStatus.APPLIED);
        if (!existingAfterSales.isEmpty()) {
            log.warn("[AfterSaleService] Order already has pending after sale, orderNo={}", orderNo);
            throw new BizException(ErrorCode.BIZ_ERROR, "该订单已有进行中的售后申请");
        }

        // 2. 生成售后单号
        String asNo = generateAsNo();

        // 3. 计算退款总金额
        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        for (ApplyAfterSaleRequest.RefundItemRequest item : request.getItems()) {
            if (item.getRefundAmount() != null) {
                totalRefundAmount = totalRefundAmount.add(item.getRefundAmount());
            }
        }

        // 4. 创建售后单
        AfterSale afterSale = new AfterSale();
        afterSale.setId(IdUtil.getSnowflakeNextId());
        afterSale.setAsNo(asNo);
        afterSale.setOrderNo(orderNo);
        afterSale.setUserId(request.getUserId());
        afterSale.setType(request.getType() != null ? request.getType() : AfterSaleType.REFUND.getCode());
        afterSale.setStatus(AfterSaleStatus.APPLIED.getCode());
        afterSale.setReason(request.getReason());
        afterSale.setRefundAmount(totalRefundAmount);
        afterSale.setVersion(1);
        afterSale.setDeleted(0);

        afterSaleRepository.save(afterSale);

        // 5. 创建售后明细
        List<AfterSaleItem> items = request.getItems().stream().map(itemReq -> {
            AfterSaleItem item = new AfterSaleItem();
            item.setAsNo(asNo);
            item.setOrderNo(orderNo);
            item.setOrderItemId(itemReq.getOrderItemId());
            item.setSkuId(itemReq.getSkuId());
            item.setQty(itemReq.getQty());
            item.setRefundAmount(itemReq.getRefundAmount() != null ? itemReq.getRefundAmount() : BigDecimal.ZERO);
            item.setOriginalPrice(BigDecimal.ZERO); // 从订单快照获取
            item.setPayableAmount(itemReq.getRefundAmount() != null ? itemReq.getRefundAmount() : BigDecimal.ZERO);
            item.setCreatedAt(LocalDateTime.now());
            return item;
        }).collect(Collectors.toList());

        afterSaleItemRepository.saveBatch(items);

        // 6. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, null, AfterSaleStatus.APPLIED.getCode(),
                "APPLY", UUID.randomUUID().toString(),
                String.valueOf(request.getUserId()),
                TraceContext.getTraceId(),
                "用户申请售后: " + request.getReason()
        );
        stateFlowRepository.save(stateFlow);

        log.info("[AfterSaleService] applyAfterSale success, asNo={}, orderNo={}", asNo, orderNo);

        return buildAfterSaleResponse(afterSale, items);
    }

    /**
     * 审批通过
     */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse approveAfterSale(ApproveAfterSaleRequest request) {
        String asNo = request.getAsNo();
        log.info("[AfterSaleService] approveAfterSale start, asNo={}", asNo);

        // 1. 查询售后单
        AfterSale afterSale = afterSaleRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "售后单不存在: " + asNo));

        // 2. 检查状态机
        if (!AfterSaleStateMachine.canTransition(afterSale.getStatusEnum(), 
                AfterSaleStateMachine.Event.APPROVE)) {
            throw new BizException(ErrorCode.BIZ_ERROR, 
                    "当前状态不允许审批: " + afterSale.getStatusEnum().getDesc());
        }

        // 3. CAS更新状态
        boolean updated = afterSaleRepository.casApprove(asNo, request.getApprovedBy(), afterSale.getVersion());
        if (!updated) {
            throw new BizException(ErrorCode.CONCURRENT_UPDATE, "并发更新，请重试");
        }

        // 4. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, AfterSaleStatus.APPLIED.getCode(), AfterSaleStatus.APPROVED.getCode(),
                AfterSaleStateMachine.Event.APPROVE.getCode(), UUID.randomUUID().toString(),
                request.getApprovedBy(),
                TraceContext.getTraceId(),
                "审批通过"
        );
        stateFlowRepository.save(stateFlow);

        // 5. 自动发起退款
        AfterSale updatedAfterSale = afterSaleRepository.findByAsNo(asNo).orElse(afterSale);
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(asNo);
        
        try {
            startRefund(updatedAfterSale, items);
        } catch (Exception e) {
            log.error("[AfterSaleService] Auto start refund failed, asNo={}, error={}", asNo, e.getMessage(), e);
            // 不抛出异常，允许后续手动重试
        }

        log.info("[AfterSaleService] approveAfterSale success, asNo={}", asNo);

        return buildAfterSaleResponse(updatedAfterSale, items);
    }

    /**
     * 审批拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse rejectAfterSale(RejectAfterSaleRequest request) {
        String asNo = request.getAsNo();
        log.info("[AfterSaleService] rejectAfterSale start, asNo={}", asNo);

        // 1. 查询售后单
        AfterSale afterSale = afterSaleRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "售后单不存在: " + asNo));

        // 2. 检查状态机
        if (!AfterSaleStateMachine.canTransition(afterSale.getStatusEnum(), 
                AfterSaleStateMachine.Event.REJECT)) {
            throw new BizException(ErrorCode.BIZ_ERROR, 
                    "当前状态不允许拒绝: " + afterSale.getStatusEnum().getDesc());
        }

        // 3. CAS更新状态
        boolean updated = afterSaleRepository.casReject(asNo, request.getRejectReason(), 
                request.getApprovedBy(), afterSale.getVersion());
        if (!updated) {
            throw new BizException(ErrorCode.CONCURRENT_UPDATE, "并发更新，请重试");
        }

        // 4. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, AfterSaleStatus.APPLIED.getCode(), AfterSaleStatus.REJECTED.getCode(),
                AfterSaleStateMachine.Event.REJECT.getCode(), UUID.randomUUID().toString(),
                request.getApprovedBy(),
                TraceContext.getTraceId(),
                "审批拒绝: " + request.getRejectReason()
        );
        stateFlowRepository.save(stateFlow);

        log.info("[AfterSaleService] rejectAfterSale success, asNo={}", asNo);

        AfterSale updatedAfterSale = afterSaleRepository.findByAsNo(asNo).orElse(afterSale);
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(asNo);
        return buildAfterSaleResponse(updatedAfterSale, items);
    }

    /**
     * 取消售后
     */
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleResponse cancelAfterSale(String asNo, Long userId) {
        log.info("[AfterSaleService] cancelAfterSale start, asNo={}, userId={}", asNo, userId);

        // 1. 查询售后单
        AfterSale afterSale = afterSaleRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "售后单不存在: " + asNo));

        // 2. 检查用户权限
        if (!afterSale.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权操作此售后单");
        }

        // 3. 检查状态机
        if (!AfterSaleStateMachine.canTransition(afterSale.getStatusEnum(), 
                AfterSaleStateMachine.Event.CANCEL)) {
            throw new BizException(ErrorCode.BIZ_ERROR, 
                    "当前状态不允许取消: " + afterSale.getStatusEnum().getDesc());
        }

        // 4. CAS更新状态
        boolean updated = afterSaleRepository.casCancel(asNo, afterSale.getVersion());
        if (!updated) {
            throw new BizException(ErrorCode.CONCURRENT_UPDATE, "并发更新，请重试");
        }

        // 5. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, AfterSaleStatus.APPLIED.getCode(), AfterSaleStatus.CANCELED.getCode(),
                AfterSaleStateMachine.Event.CANCEL.getCode(), UUID.randomUUID().toString(),
                String.valueOf(userId),
                TraceContext.getTraceId(),
                "用户取消售后"
        );
        stateFlowRepository.save(stateFlow);

        log.info("[AfterSaleService] cancelAfterSale success, asNo={}", asNo);

        AfterSale updatedAfterSale = afterSaleRepository.findByAsNo(asNo).orElse(afterSale);
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(asNo);
        return buildAfterSaleResponse(updatedAfterSale, items);
    }

    /**
     * 发起退款
     */
    @Transactional(rollbackFor = Exception.class)
    public void startRefund(AfterSale afterSale, List<AfterSaleItem> items) {
        String asNo = afterSale.getAsNo();
        log.info("[AfterSaleService] startRefund start, asNo={}", asNo);

        // 1. 检查状态
        if (afterSale.getStatusEnum() != AfterSaleStatus.APPROVED) {
            throw new BizException(ErrorCode.BIZ_ERROR, "当前状态不允许发起退款");
        }

        // 2. 调用支付服务创建退款单
        String refundNo = paymentClient.createRefund(
                afterSale.getOrderNo(),
                asNo,
                afterSale.getRefundAmount(),
                items.stream().map(item -> PaymentClient.RefundItem.builder()
                        .skuId(item.getSkuId())
                        .qty(item.getQty())
                        .refundAmount(item.getRefundAmount())
                        .build()
                ).collect(Collectors.toList())
        );

        // 3. CAS更新状态为退款中
        boolean updated = afterSaleRepository.casStartRefund(asNo, refundNo, afterSale.getVersion());
        if (!updated) {
            log.warn("[AfterSaleService] startRefund CAS failed, asNo={}", asNo);
            // 不抛出异常，退款单已创建
        }

        // 4. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, AfterSaleStatus.APPROVED.getCode(), AfterSaleStatus.REFUNDING.getCode(),
                AfterSaleStateMachine.Event.START_REFUND.getCode(), UUID.randomUUID().toString(),
                "system",
                TraceContext.getTraceId(),
                "发起退款，退款单号: " + refundNo
        );
        stateFlowRepository.save(stateFlow);

        log.info("[AfterSaleService] startRefund success, asNo={}, refundNo={}", asNo, refundNo);
    }

    /**
     * 处理退款成功回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRefundSuccess(String asNo, String refundNo, LocalDateTime refundedAt) {
        log.info("[AfterSaleService] handleRefundSuccess start, asNo={}, refundNo={}", asNo, refundNo);

        // 1. 查询售后单
        AfterSale afterSale = afterSaleRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "售后单不存在: " + asNo));

        // 2. 幂等检查
        if (afterSale.getStatusEnum() == AfterSaleStatus.REFUNDED) {
            log.info("[AfterSaleService] handleRefundSuccess idempotent, asNo={}", asNo);
            return;
        }

        // 3. 检查状态
        if (afterSale.getStatusEnum() != AfterSaleStatus.REFUNDING) {
            log.warn("[AfterSaleService] handleRefundSuccess invalid status, asNo={}, status={}", 
                    asNo, afterSale.getStatus());
            return;
        }

        // 4. CAS更新状态为已退款
        boolean updated = afterSaleRepository.casRefunded(asNo);
        if (!updated) {
            log.warn("[AfterSaleService] handleRefundSuccess CAS failed, asNo={}", asNo);
            return;
        }

        // 5. 记录状态流转
        AfterSaleStateFlow stateFlow = AfterSaleStateFlow.build(
                asNo, AfterSaleStatus.REFUNDING.getCode(), AfterSaleStatus.REFUNDED.getCode(),
                AfterSaleStateMachine.Event.REFUND_SUCCESS.getCode(), UUID.randomUUID().toString(),
                "system",
                TraceContext.getTraceId(),
                "退款成功"
        );
        stateFlowRepository.save(stateFlow);

        // 6. 发送售后退款完成事件（通知订单服务和库存服务）
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(asNo);
        publishAfterSaleRefundedEvent(afterSale, items, refundedAt);

        log.info("[AfterSaleService] handleRefundSuccess success, asNo={}", asNo);
    }

    /**
     * 发布售后退款完成事件
     */
    private void publishAfterSaleRefundedEvent(AfterSale afterSale, List<AfterSaleItem> items, 
                                                LocalDateTime refundedAt) {
        AfterSaleRefundedEvent event = AfterSaleRefundedEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .asNo(afterSale.getAsNo())
                .orderNo(afterSale.getOrderNo())
                .userId(afterSale.getUserId())
                .refundAmount(afterSale.getRefundAmount())
                .refundNo(afterSale.getRefundNo())
                .items(items.stream().map(item -> AfterSaleRefundedEvent.RefundItemInfo.builder()
                        .orderItemId(item.getOrderItemId())
                        .skuId(item.getSkuId())
                        .qty(item.getQty())
                        .refundAmount(item.getRefundAmount())
                        .build()
                ).collect(Collectors.toList()))
                .refundedAt(refundedAt)
                .eventTime(LocalDateTime.now())
                .traceId(TraceContext.getTraceId())
                .version("1.0")
                .build();

        try {
            producerTemplate.syncSend(AFTERSALES_TOPIC, TAG_AFTERSALE_REFUNDED, 
                    afterSale.getOrderNo(), event);
            log.info("[AfterSaleService] AfterSaleRefunded event published, asNo={}, orderNo={}",
                    afterSale.getAsNo(), afterSale.getOrderNo());
        } catch (Exception e) {
            log.error("[AfterSaleService] Failed to publish AfterSaleRefunded event, asNo={}, error={}",
                    afterSale.getAsNo(), e.getMessage(), e);
        }
    }

    /**
     * 查询售后单
     */
    public AfterSaleResponse getAfterSale(String asNo) {
        AfterSale afterSale = afterSaleRepository.findByAsNo(asNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "售后单不存在: " + asNo));
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(asNo);
        return buildAfterSaleResponse(afterSale, items);
    }

    /**
     * 根据订单号查询售后单
     */
    public AfterSaleResponse getAfterSaleByOrderNo(String orderNo) {
        AfterSale afterSale = afterSaleRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单对应的售后单不存在: " + orderNo));
        List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(afterSale.getAsNo());
        return buildAfterSaleResponse(afterSale, items);
    }

    /**
     * 查询用户售后单列表
     */
    public List<AfterSaleResponse> getAfterSalesByUserId(Long userId) {
        List<AfterSale> afterSales = afterSaleRepository.findByUserId(userId);
        return afterSales.stream().map(afterSale -> {
            List<AfterSaleItem> items = afterSaleItemRepository.findByAsNo(afterSale.getAsNo());
            return buildAfterSaleResponse(afterSale, items);
        }).collect(Collectors.toList());
    }

    /**
     * 生成售后单号
     */
    private String generateAsNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "AS" + timestamp + random;
    }

    /**
     * 构建售后单响应
     */
    private AfterSaleResponse buildAfterSaleResponse(AfterSale afterSale, List<AfterSaleItem> items) {
        AfterSaleStatus status = afterSale.getStatusEnum();
        AfterSaleType type = afterSale.getTypeEnum();

        return AfterSaleResponse.builder()
                .id(afterSale.getId())
                .asNo(afterSale.getAsNo())
                .orderNo(afterSale.getOrderNo())
                .userId(afterSale.getUserId())
                .type(afterSale.getType())
                .typeDesc(type.getDesc())
                .status(afterSale.getStatus())
                .statusDesc(status.getDesc())
                .reason(afterSale.getReason())
                .refundAmount(afterSale.getRefundAmount())
                .refundNo(afterSale.getRefundNo())
                .rejectReason(afterSale.getRejectReason())
                .approvedAt(afterSale.getApprovedAt())
                .approvedBy(afterSale.getApprovedBy())
                .refundedAt(afterSale.getRefundedAt())
                .createdAt(afterSale.getCreatedAt())
                .updatedAt(afterSale.getUpdatedAt())
                .items(items.stream().map(item -> AfterSaleResponse.AfterSaleItemResponse.builder()
                        .id(item.getId())
                        .orderItemId(item.getOrderItemId())
                        .skuId(item.getSkuId())
                        .qty(item.getQty())
                        .refundAmount(item.getRefundAmount())
                        .originalPrice(item.getOriginalPrice())
                        .payableAmount(item.getPayableAmount())
                        .build()
                ).collect(Collectors.toList()))
                .build();
    }
}
