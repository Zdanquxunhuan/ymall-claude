package com.yuge.payment.application;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.payment.api.dto.*;
import com.yuge.payment.domain.entity.PayCallbackLog;
import com.yuge.payment.domain.entity.PayOrder;
import com.yuge.payment.domain.enums.CallbackProcessResult;
import com.yuge.payment.domain.enums.PayChannel;
import com.yuge.payment.domain.enums.PayStatus;
import com.yuge.payment.domain.event.PaymentSucceededEvent;
import com.yuge.payment.infrastructure.repository.PayCallbackLogRepository;
import com.yuge.payment.infrastructure.repository.PayOrderRepository;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 支付应用服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String PAYMENT_TOPIC = "PAYMENT_TOPIC";
    private static final String TAG_PAYMENT_SUCCEEDED = "PAYMENT_SUCCEEDED";

    private final PayOrderRepository payOrderRepository;
    private final PayCallbackLogRepository payCallbackLogRepository;
    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;

    @Value("${payment.callback-secret}")
    private String callbackSecret;

    @Value("${payment.timeout-minutes:30}")
    private int timeoutMinutes;

    /**
     * 创建支付单（按order_no幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        String orderNo = request.getOrderNo();

        // 1. 幂等检查：根据orderNo查询是否已存在支付单
        Optional<PayOrder> existingPayOrder = payOrderRepository.findByOrderNo(orderNo);
        if (existingPayOrder.isPresent()) {
            PayOrder payOrder = existingPayOrder.get();
            log.info("[PaymentService] Payment already exists, orderNo={}, payNo={}, status={}",
                    orderNo, payOrder.getPayNo(), payOrder.getStatus());
            return buildPaymentResponse(payOrder);
        }

        // 2. 生成支付单号
        String payNo = generatePayNo();

        // 3. 确定支付渠道
        String channel = request.getChannel();
        if (channel == null || channel.isEmpty()) {
            channel = PayChannel.MOCK.getCode();
        }

        // 4. 创建支付单
        PayOrder payOrder = new PayOrder();
        payOrder.setId(IdUtil.getSnowflakeNextId());
        payOrder.setPayNo(payNo);
        payOrder.setOrderNo(orderNo);
        payOrder.setAmount(request.getAmount());
        payOrder.setStatus(PayStatus.INIT.getCode());
        payOrder.setChannel(channel);
        payOrder.setExpireAt(LocalDateTime.now().plusMinutes(timeoutMinutes));

        payOrderRepository.save(payOrder);

        log.info("[PaymentService] Payment created, payNo={}, orderNo={}, amount={}, channel={}",
                payNo, orderNo, request.getAmount(), channel);

        return buildPaymentResponse(payOrder);
    }

    /**
     * 查询支付单
     */
    public PaymentResponse getPayment(String payNo) {
        PayOrder payOrder = payOrderRepository.findByPayNo(payNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "支付单不存在: " + payNo));
        return buildPaymentResponse(payOrder);
    }

    /**
     * 根据订单号查询支付单
     */
    public PaymentResponse getPaymentByOrderNo(String orderNo) {
        PayOrder payOrder = payOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "订单对应的支付单不存在: " + orderNo));
        return buildPaymentResponse(payOrder);
    }

    /**
     * 处理模拟回调（验签 + 幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public CallbackResponse handleMockCallback(MockCallbackRequest request) {
        String payNo = request.getPayNo();
        String callbackStatus = request.getCallbackStatus();

        // 1. 生成回调唯一ID（用于幂等）
        String callbackId = generateCallbackId(payNo, request.getTimestamp(), request.getNonce());

        // 2. 验签
        boolean signatureValid = verifySignature(request);

        // 3. 记录回调日志（无论验签是否通过）
        PayCallbackLog callbackLog = new PayCallbackLog();
        callbackLog.setPayNo(payNo);
        callbackLog.setCallbackId(callbackId);
        callbackLog.setChannel(PayChannel.MOCK.getCode());
        callbackLog.setChannelTradeNo(request.getChannelTradeNo());
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
        Optional<PayCallbackLog> existingCallback = payCallbackLogRepository.findByCallbackId(callbackId);
        if (existingCallback.isPresent()) {
            PayCallbackLog existing = existingCallback.get();
            log.info("[PaymentService] Callback already processed, callbackId={}, result={}",
                    callbackId, existing.getProcessResult());

            // 查询当前支付单状态
            PayOrder payOrder = payOrderRepository.findByPayNo(payNo).orElse(null);
            String currentStatus = payOrder != null ? payOrder.getStatus() : "UNKNOWN";

            return CallbackResponse.ignored(payNo, currentStatus, "重复回调，已忽略");
        }

        // 5. 验签失败处理
        if (!signatureValid) {
            callbackLog.setProcessResult(CallbackProcessResult.FAILED.getCode());
            callbackLog.setProcessMessage("签名验证失败");
            payCallbackLogRepository.save(callbackLog);

            log.warn("[PaymentService] Callback signature invalid, payNo={}", payNo);
            return CallbackResponse.failed(payNo, "签名验证失败");
        }

        // 6. 查询支付单
        Optional<PayOrder> payOrderOpt = payOrderRepository.findByPayNo(payNo);
        if (payOrderOpt.isEmpty()) {
            callbackLog.setProcessResult(CallbackProcessResult.FAILED.getCode());
            callbackLog.setProcessMessage("支付单不存在");
            payCallbackLogRepository.save(callbackLog);

            log.warn("[PaymentService] Payment not found for callback, payNo={}", payNo);
            return CallbackResponse.failed(payNo, "支付单不存在");
        }

        PayOrder payOrder = payOrderOpt.get();
        PayStatus currentStatus = PayStatus.of(payOrder.getStatus());

        // 7. 检查状态是否允许接收回调
        if (!currentStatus.canReceiveCallback()) {
            callbackLog.setProcessResult(CallbackProcessResult.IGNORED.getCode());
            callbackLog.setProcessMessage("当前状态不允许接收回调: " + currentStatus.getDesc());
            payCallbackLogRepository.save(callbackLog);

            log.info("[PaymentService] Callback ignored, payNo={}, currentStatus={}", payNo, currentStatus);
            return CallbackResponse.ignored(payNo, currentStatus.getCode(), 
                    "当前状态[" + currentStatus.getDesc() + "]不允许接收回调");
        }

        // 8. 处理回调
        boolean updated;
        String newStatus;

        if ("SUCCESS".equals(callbackStatus)) {
            // 支付成功
            updated = payOrderRepository.casUpdateToSuccess(payNo, request.getChannelTradeNo(), LocalDateTime.now());
            newStatus = PayStatus.SUCCESS.getCode();

            if (updated) {
                // 发送支付成功事件
                publishPaymentSucceededEvent(payOrder, request.getChannelTradeNo());
            }
        } else {
            // 支付失败
            updated = payOrderRepository.casUpdateToFailed(payNo);
            newStatus = PayStatus.FAILED.getCode();
        }

        if (updated) {
            callbackLog.setProcessResult(CallbackProcessResult.PROCESSED.getCode());
            callbackLog.setProcessMessage("状态更新成功: " + currentStatus.getCode() + " -> " + newStatus);
            payCallbackLogRepository.save(callbackLog);

            log.info("[PaymentService] Callback processed, payNo={}, {} -> {}", payNo, currentStatus, newStatus);
            return CallbackResponse.success(payNo, newStatus);
        } else {
            // CAS更新失败，可能是并发
            PayOrder refreshed = payOrderRepository.findByPayNo(payNo).orElse(payOrder);
            callbackLog.setProcessResult(CallbackProcessResult.IGNORED.getCode());
            callbackLog.setProcessMessage("CAS更新失败，当前状态: " + refreshed.getStatus());
            payCallbackLogRepository.save(callbackLog);

            log.warn("[PaymentService] CAS update failed, payNo={}, currentStatus={}", payNo, refreshed.getStatus());
            return CallbackResponse.ignored(payNo, refreshed.getStatus(), "并发更新，已忽略");
        }
    }

    /**
     * 发布支付成功事件
     */
    public void publishPaymentSucceededEvent(PayOrder payOrder, String channelTradeNo) {
        PaymentSucceededEvent event = PaymentSucceededEvent.builder()
                .eventId(UUID.randomUUID().toString().replace("-", ""))
                .payNo(payOrder.getPayNo())
                .orderNo(payOrder.getOrderNo())
                .amount(payOrder.getAmount())
                .channel(payOrder.getChannel())
                .channelTradeNo(channelTradeNo)
                .paidAt(LocalDateTime.now())
                .eventTime(LocalDateTime.now())
                .traceId(TraceContext.getTraceId())
                .version("1.0")
                .build();

        try {
            producerTemplate.syncSend(PAYMENT_TOPIC, TAG_PAYMENT_SUCCEEDED, payOrder.getOrderNo(), event);
            log.info("[PaymentService] PaymentSucceeded event published, payNo={}, orderNo={}",
                    payOrder.getPayNo(), payOrder.getOrderNo());
        } catch (Exception e) {
            log.error("[PaymentService] Failed to publish PaymentSucceeded event, payNo={}, error={}",
                    payOrder.getPayNo(), e.getMessage(), e);
            // 不抛出异常，避免影响回调处理
            // 补单机制会处理未发送的事件
        }
    }

    /**
     * 验证签名
     * 签名规则: MD5(payNo + callbackStatus + timestamp + nonce + secret)
     */
    public boolean verifySignature(MockCallbackRequest request) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("payNo", request.getPayNo());
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
    public String generateSignature(String payNo, String callbackStatus, String timestamp, String nonce) {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("payNo", payNo);
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
     * 生成支付单号
     * 格式: PAY + 年月日时分秒毫秒 + 4位随机数
     */
    private String generatePayNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String random = String.valueOf((int) ((Math.random() * 9000) + 1000));
        return "PAY" + timestamp + random;
    }

    /**
     * 生成回调唯一ID
     */
    private String generateCallbackId(String payNo, String timestamp, String nonce) {
        return SecureUtil.md5(payNo + timestamp + nonce);
    }

    /**
     * 构建支付单响应
     */
    private PaymentResponse buildPaymentResponse(PayOrder payOrder) {
        PayStatus status = PayStatus.of(payOrder.getStatus());
        return PaymentResponse.builder()
                .id(payOrder.getId())
                .payNo(payOrder.getPayNo())
                .orderNo(payOrder.getOrderNo())
                .amount(payOrder.getAmount())
                .status(payOrder.getStatus())
                .statusDesc(status.getDesc())
                .channel(payOrder.getChannel())
                .channelTradeNo(payOrder.getChannelTradeNo())
                .paidAt(payOrder.getPaidAt())
                .expireAt(payOrder.getExpireAt())
                .createdAt(payOrder.getCreatedAt())
                .updatedAt(payOrder.getUpdatedAt())
                .build();
    }
}
