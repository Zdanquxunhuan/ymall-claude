package com.yuge.payment.application;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.payment.api.dto.*;
import com.yuge.payment.domain.entity.PayOrder;
import com.yuge.payment.domain.entity.RefundCallbackLog;
import com.yuge.payment.domain.entity.RefundOrder;
import com.yuge.payment.domain.enums.CallbackProcessResult;
import com.yuge.payment.domain.enums.PayChannel;
import com.yuge.payment.domain.enums.RefundStatus;
import com.yuge.payment.domain.event.RefundSucceededEvent;
import com.yuge.payment.infrastructure.repository.PayOrderRepository;
import com.yuge.payment.infrastructure.repository.RefundCallbackLogRepository;
import com.yuge.payment.infrastructure.repository.RefundOrderRepository;
import com.yuge.platform.infra.common.ErrorCode;
import com.yuge.platform.infra.exception.BizException;
import com.yuge.platform.infra.mq.ProducerTemplate;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 退款应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private static final String PAYMENT_TOPIC = "PAYMENT_TOPIC";
    private static final String TAG_REFUND_SUCCEEDED = "REFUND_SUCCEEDED";

    private final RefundOrderRepository refundOrderRepository;
    private final RefundCallbackLogRepository refundCallbackLogRepository;
    private final PayOrderRepository payOrderRepository;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.callback-secret}")
    private String callbackSecret;

    /**
     * 创建退款单（按order_no幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public RefundResponse createRefund(CreateRefundRequest request) {
        String orderNo = request.getOrderNo();
        log.info("[RefundService] createRefund start, orderNo={}, asNo={}, amount={}",
                orderNo, request.getAsNo(), request.getAmount());

        // 1. 幂等检查：根据orderNo查询是否已存在退款单
        Optional<RefundOrder> existingRefund = refundOrderRepository.findByOrderNo(orderNo);
        if (existingRefund.isPresent()) {
            RefundOrder refundOrder = existingRefund.get();
            log.info("[RefundService] Refund already exists, orderNo={}, refundNo={}, status={}",
                    orderNo, refundOrder.getRefundNo(), refundOrder.getStatus());
            return buildRefundResponse(refundOrder);
        }

        // 2. 查询原支付单
        PayOrder payOrder = payOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在: " + orderNo));

        // 3. 检查支付单状态
        if (!"SUCCESS".equals(payOrder.getStatus())) {
            throw new BizException(ErrorCode.BIZ_ERROR, "支付单状态不允许退款: " + payOrder.getStatus());
        }

        // 4. 检查退款金额
        if (request.getAmount().compareTo(payOrder.getAmount()) > 0) {
            throw new BizException(ErrorCode.BIZ_ERROR, "退款金额不能大于支付金额");
        }

        // 5. 生成退款单号
        String refundNo = generateRefundNo();

        // 6. 序列化退款明细
        String itemsJson = null;
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            try {
                itemsJson = objectMapper.writeValueAsString(request.getItems());
            } catch (JsonProcessingException e) {
                log.warn("[RefundService] Failed to serialize items, error={}", e.getMessage());
            }
        }

        // 7. 创建退款单
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setId(IdUtil.getSnowflakeNextId());
        refundOrder.setRefundNo(refundNo);
        refundOrder.setPayNo(payOrder.getPayNo());
        refundOrder.setOrderNo(orderNo);
        refundOrder.setAsNo(request.getAsNo());
        refundOrder.setAmount(request.getAmount());
        refundOrder.setStatus(RefundStatus.INIT.getCode());
        refundOrder.setChannel(payOrder.getChannel());
        refundOrder.setRefundReason(request.getReason());
        refundOrder.setItemsJson(itemsJson);
        refundOrder.setVersion(1);
        refundOrder.setDeleted(0);

        refundOrderRepository.save(refundOrder);

        // 8. 更新状态为退款中（模拟发起退款请求）
        refundOrderRepository.casUpdateToRefunding(refundNo);

        log.info("[RefundService] Refund created, refundNo={}, orderNo={}, amount={}",
                refundNo, orderNo, request.getAmount());

        // 重新查询获取最新状态
        RefundOrder savedRefund = refundOrderRepository.findByRefundNo(refundNo).orElse(refundOrder);
        return buildRefundResponse(savedRefund);
    }

    /**
     * 查询退款单
     */
    public RefundResponse getRefund(String refundNo) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "退款单不存在: " + refundNo));
        return buildRefundResponse(refundOrder);
    }

    /**
     * 根据订单号查询退款单
     */
    public RefundResponse getRefundByOrderNo(String orderNo) {
        RefundOrder refundOrder = refundOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单对应的退款单不存在: " + orderNo));
        return buildRefundResponse(refundOrder);
    }

    /**
     * 处理模拟退款回调（验签 + 幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public RefundCallbackResponse handleMockRefundCallback(MockRefundCallbackRequest request) {
        String refundNo = request.getRefundNo();
        String callbackStatus = request.getCallbackStatus();

        log.info("[RefundService] handleMockRefundCallback start, refundNo={}, status={}",
                refundNo, callbackStatus);

        // 1. 生成回调唯一ID（用于幂等）
        String callbackId = generateCallbackId(refundNo, request.getTimestamp(), request.getNonce());

        // 2. 验签
        boolean signatureValid = verifySignature(request);

        // 3. 记录回调日志
        RefundCallbackLog callbackLog = new RefundCallbackLog();
        callbackLog.setRefundNo(refundNo);
        callbackLog.setCallbackId(callbackId);
        callbackLog.setChannel(PayChannel.MOCK.getCode());
        callbackLog.setChannelRefundNo(request.getChannelRefundNo());
        callbackLog.setCallbackStatus(callbackStatus);
        callbackLog.setSignature(request.getSignature());
        callbackLog.setSignatureValid(signatureValid ? 1 : 0);
        callbackLog.setTraceId(TraceContext.getTraceId());

        try {
            callbackLog.setRawPayload(objectMapper.writeValueAsString(request));
        } catch (Exception e) {
            callbackLog.setRawPayload(request.toString());
        }

        // 4. 幂等检查
        Optional<RefundCallbackLog> existingCallback = refundCallbackLogRepository.findByCallbackId(callbackId);
        if (existingCallback.isPresent()) {
            log.info("[RefundService] Callback already processed, callbackId={}", callbackId);
            RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo).orElse(null);
            String currentStatus = refundOrder != null ? refundOrder.getStatus() : "UNKNOWN";
            return RefundCallbackResponse.ignored(refundNo, currentStatus, "重复回调，已忽略");
        }

        // 5. 验签失败处理
        if (!signatureValid) {
            callbackLog.setProcessResult(CallbackProcessResult.FAILED.getCode());
            callbackLog.setProcessMessage("签名验证失败");
            refundCallbackLogRepository.save(callbackLog);

            log.warn("[RefundService] Callback signature invalid, refundNo={}", refundNo);
            return RefundCallbackResponse.failed(refundNo, "签名验证失败");
        }

        // 6. 查询退款单
        Optional<RefundOrder> refundOrderOpt = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrderOpt.isEmpty()) {
            callbackLog.setProcessResult(CallbackProcessResult.FAILED.getCode());
            callbackLog.setProcessMessage("退款单不存在");
            refundCallbackLogRepository.save(callbackLog);

            log.warn("[RefundService] Refund not found for callback, refundNo={}", refundNo);
            return RefundCallbackResponse.failed(refundNo, "退款单不存在");
        }

        RefundOrder refundOrder = refundOrderOpt.get();
        RefundStatus currentStatus = RefundStatus.of(refundOrder.getStatus());

        // 7. 检查状态是否允许接收回调
        if (!currentStatus.canReceiveCallback()) {
            callbackLog.setProcessResult(CallbackProcessResult.IGNORED.getCode());
            callbackLog.setProcessMessage("当前状态不允许接收回调: " + currentStatus.getDesc());
            refundCallbackLogRepository.save(callbackLog);

            log.info("[RefundService] Callback ignored, refundNo={}, currentStatus={}", refundNo, currentStatus);
            return RefundCallbackResponse.ignored(refundNo, currentStatus.getCode(),
                    "当前状态[" + currentStatus.getDesc() + "]不允许接收回调");
        }

        // 8. 处理回调
        boolean updated;
        String newStatus;

        if ("SUCCESS".equals(callbackStatus)) {
            // 退款成功
            updated = refundOrderRepository.casUpdateToSuccess(refundNo, request.getChannelRefundNo());
            newStatus = RefundStatus.SUCCESS.getCode();

            if (updated) {
                // 发送退款成功事件
                publishRefundSucceededEvent(refundOrder, request.getChannelRefundNo());
            }
        } else {
            // 退款失败
            updated = refundOrderRepository.casUpdateToFailed(refundNo);
            newStatus = RefundStatus.FAILED.getCode();
        }

        if (updated) {
            callbackLog.setProcessResult(CallbackProcessResult.PROCESSED.getCode());
            callbackLog.setProcessMessage("状态更新成功: " + currentStatus.getCode() + " -> " + newStatus);
            refundCallbackLogRepository.save(callbackLog);

            log.info("[RefundService] Callback processed, refundNo={}, {} -> {}", refundNo, currentStatus, newStatus);
            return RefundCallbackResponse.success(refundNo, newStatus);
        } else {
            RefundOrder refreshed = refundOrderRepository.findByRefundNo(refundNo).orElse(refundOrder);
            callbackLog.setProcessResult(CallbackProcessResult.IGNORED.getCode());
            callbackLog.setProcessMessage("CAS更新失败，当前状态: " + refreshed.getStatus());
            refundCallbackLogRepository.save(callbackLog);

            log.warn("[RefundService] CAS update failed, refundNo={}, currentStatus={}", refundNo, refreshed.getStatus());
            return RefundCallbackResponse.ignored(refundNo, refreshed.getStatus(), "并发更新，已忽略");
        }
    }

    /**
     * 发布退款成功事件
     */
    private void publishRefundSucceededEvent(RefundOrder refundOrder, String channelRefundNo) {
        // 解析退款明细
        List<RefundSucceededEvent.RefundItemInfo> items = null;
        if (refundOrder.getItemsJson() != null) {
            try {
                List<CreateRefundRequest.RefundItemRequest> itemRequests = objectMapper.readValue(
                        refundOrder.getItemsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(
                                List.class, CreateRefundRequest.RefundItemRequest.class));
                items = itemRequests.stream()
                        .map(item -> RefundSucceededEvent.RefundItemInfo.builder()
                                .skuId(item.getSkuId())
                                .qty(item.getQty())
                                .refundAmount(item.getRefundAmount())
                                .build())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("[RefundService] Failed to parse items json, error={}", e.getMessage());
            }
        }

        RefundSucceededEvent event = RefundSucceededEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .refundNo(refundOrder.getRefundNo())
                .payNo(refundOrder.getPayNo())
                .orderNo(refundOrder.getOrderNo())
                .asNo(refundOrder.getAsNo())
                .amount(refundOrder.getAmount())
                .channel(refundOrder.getChannel())
                .channelRefundNo(channelRefundNo)
                .refundedAt(LocalDateTime.now())
                .items(items)
                .eventTime(LocalDateTime.now())
                .traceId(TraceContext.getTraceId())
                .version("1.0")
                .build();

        try {
            producerTemplate.syncSend(PAYMENT_TOPIC, TAG_REFUND_SUCCEEDED, refundOrder.getOrderNo(), event);
            log.info("[RefundService] RefundSucceeded event published, refundNo={}, orderNo={}",
                    refundOrder.getRefundNo(), refundOrder.getOrderNo());
        } catch (Exception e) {
            log.error("[RefundService] Failed to publish RefundSucceeded event, refundNo={}, error={}",
                    refundOrder.getRefundNo(), e.getMessage(), e);
        }
    }

    /**
     * 验证签名
     */
    public boolean verifySignature(MockRefundCallbackRequest request) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("refundNo", request.getRefundNo());
        params.put("callbackStatus", request.getCallbackStatus());
        params.put("timestamp", request.getTimestamp());
        params.put("nonce", request.getNonce());

        StringBuilder sb = new StringBuilder();
        for (String value : params.values()) {
            sb.append(value);
        }
        sb.append(callbackSecret);

        String expectedSign = SecureUtil.md5(sb.toString()).toUpperCase();
        return expectedSign.equals(request.getSignature());
    }

    /**
     * 生成签名（供测试使用）
     */
    public String generateSignature(String refundNo, String callbackStatus, String timestamp, String nonce) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("refundNo", refundNo);
        params.put("callbackStatus", callbackStatus);
        params.put("timestamp", timestamp);
        params.put("nonce", nonce);

        StringBuilder sb = new StringBuilder();
        for (String value : params.values()) {
            sb.append(value);
        }
        sb.append(callbackSecret);

        return SecureUtil.md5(sb.toString()).toUpperCase();
    }

    /**
     * 生成退款单号
     */
    private String generateRefundNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "RF" + timestamp + random;
    }

    /**
     * 生成回调唯一ID
     */
    private String generateCallbackId(String refundNo, String timestamp, String nonce) {
        return SecureUtil.md5(refundNo + timestamp + nonce);
    }

    /**
     * 构建退款响应
     */
    private RefundResponse buildRefundResponse(RefundOrder refundOrder) {
        RefundStatus status = RefundStatus.of(refundOrder.getStatus());
        return RefundResponse.builder()
                .id(refundOrder.getId())
                .refundNo(refundOrder.getRefundNo())
                .payNo(refundOrder.getPayNo())
                .orderNo(refundOrder.getOrderNo())
                .asNo(refundOrder.getAsNo())
                .amount(refundOrder.getAmount())
                .status(refundOrder.getStatus())
                .statusDesc(status.getDesc())
                .channel(refundOrder.getChannel())
                .channelRefundNo(refundOrder.getChannelRefundNo())
                .refundedAt(refundOrder.getRefundedAt())
                .createdAt(refundOrder.getCreatedAt())
                .updatedAt(refundOrder.getUpdatedAt())
                .build();
    }
}
