package com.yuge.fulfillment.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.fulfillment.domain.entity.Shipment;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.domain.enums.ShipmentStatus;
import com.yuge.fulfillment.domain.event.ShipmentCreatedEvent;
import com.yuge.fulfillment.domain.event.ShipmentDeliveredEvent;
import com.yuge.fulfillment.domain.event.ShipmentShippedEvent;
import com.yuge.fulfillment.infrastructure.repository.ShipmentRepository;
import com.yuge.fulfillment.infrastructure.repository.WaybillRepository;
import com.yuge.platform.infra.mq.BaseEvent;
import com.yuge.platform.infra.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 发货单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private static final String FULFILLMENT_TOPIC = "FULFILLMENT_TOPIC";
    private static final String TAG_SHIPMENT_CREATED = "SHIPMENT_CREATED";
    private static final String TAG_SHIPMENT_SHIPPED = "SHIPMENT_SHIPPED";
    private static final String TAG_SHIPMENT_DELIVERED = "SHIPMENT_DELIVERED";

    private final ShipmentRepository shipmentRepository;
    private final WaybillRepository waybillRepository;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建发货单（幂等）
     *
     * @param orderNo 订单号
     * @param eventId 触发事件ID（用于幂等）
     * @return 发货单号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createShipment(String orderNo, String eventId) {
        // 幂等检查：订单是否已有发货单
        if (shipmentRepository.existsByOrderNo(orderNo)) {
            Shipment existing = shipmentRepository.findByOrderNo(orderNo)
                    .orElseThrow(() -> new IllegalStateException("发货单查询异常"));
            log.info("[ShipmentService] Shipment already exists for order, orderNo={}, shipmentNo={}",
                    orderNo, existing.getShipmentNo());
            throw new IllegalStateException("订单已有发货单: " + existing.getShipmentNo());
        }

        // 生成发货单号
        String shipmentNo = generateShipmentNo();

        // 创建发货单
        Shipment shipment = Shipment.builder()
                .id(IdWorker.getId())
                .shipmentNo(shipmentNo)
                .orderNo(orderNo)
                .status(ShipmentStatus.CREATED.getCode())
                .createdBy("SYSTEM")
                .updatedBy("SYSTEM")
                .version(0)
                .deleted(0)
                .build();

        shipmentRepository.save(shipment);

        log.info("[ShipmentService] Shipment created, shipmentNo={}, orderNo={}", shipmentNo, orderNo);

        // 发布 ShipmentCreated 事件
        publishShipmentCreatedEvent(shipmentNo, orderNo);

        return shipmentNo;
    }

    /**
     * 发货
     *
     * @param shipmentNo 发货单号
     * @param waybillNo  运单号
     * @param carrier    承运商
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean ship(String shipmentNo, String waybillNo, String carrier) {
        // 查询发货单
        Shipment shipment = shipmentRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new IllegalArgumentException("发货单不存在: " + shipmentNo));

        ShipmentStatus currentStatus = ShipmentStatus.of(shipment.getStatus());

        // 幂等检查
        if (currentStatus == ShipmentStatus.SHIPPED || currentStatus == ShipmentStatus.DELIVERED) {
            log.info("[ShipmentService] Shipment already shipped, shipmentNo={}, status={}",
                    shipmentNo, currentStatus);
            return true;
        }

        // 状态检查
        if (!currentStatus.canShip()) {
            log.warn("[ShipmentService] Cannot ship, shipmentNo={}, currentStatus={}", shipmentNo, currentStatus);
            throw new IllegalStateException("当前状态不允许发货: " + currentStatus.getDesc());
        }

        // 创建运单
        if (!waybillRepository.existsByShipmentNo(shipmentNo)) {
            Waybill waybill = Waybill.builder()
                    .id(IdWorker.getId())
                    .waybillNo(waybillNo)
                    .shipmentNo(shipmentNo)
                    .carrier(carrier)
                    .createdBy("SYSTEM")
                    .updatedBy("SYSTEM")
                    .version(0)
                    .deleted(0)
                    .build();
            waybillRepository.save(waybill);
            log.info("[ShipmentService] Waybill created, waybillNo={}, shipmentNo={}", waybillNo, shipmentNo);
        }

        // CAS更新状态
        boolean updated = shipmentRepository.casUpdateStatusToShipped(shipmentNo);
        if (!updated) {
            log.warn("[ShipmentService] CAS update failed (concurrent), shipmentNo={}", shipmentNo);
            // 重新查询状态
            Shipment refreshed = shipmentRepository.findByShipmentNo(shipmentNo).orElse(shipment);
            if (ShipmentStatus.SHIPPED.getCode().equals(refreshed.getStatus()) ||
                ShipmentStatus.DELIVERED.getCode().equals(refreshed.getStatus())) {
                return true; // 已被其他线程更新
            }
            throw new IllegalStateException("发货失败，请重试");
        }

        log.info("[ShipmentService] Shipment shipped, shipmentNo={}, waybillNo={}", shipmentNo, waybillNo);

        // 发布 ShipmentShipped 事件
        publishShipmentShippedEvent(shipmentNo, shipment.getOrderNo(), waybillNo, carrier);

        return true;
    }

    /**
     * 签收
     *
     * @param shipmentNo 发货单号
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deliver(String shipmentNo) {
        // 查询发货单
        Shipment shipment = shipmentRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new IllegalArgumentException("发货单不存在: " + shipmentNo));

        ShipmentStatus currentStatus = ShipmentStatus.of(shipment.getStatus());

        // 幂等检查
        if (currentStatus == ShipmentStatus.DELIVERED) {
            log.info("[ShipmentService] Shipment already delivered, shipmentNo={}", shipmentNo);
            return true;
        }

        // 状态检查
        if (!currentStatus.canDeliver()) {
            log.warn("[ShipmentService] Cannot deliver, shipmentNo={}, currentStatus={}", shipmentNo, currentStatus);
            throw new IllegalStateException("当前状态不允许签收: " + currentStatus.getDesc());
        }

        // 获取运单号
        Waybill waybill = waybillRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new IllegalStateException("运单不存在"));

        // CAS更新状态
        boolean updated = shipmentRepository.casUpdateStatusToDelivered(shipmentNo);
        if (!updated) {
            log.warn("[ShipmentService] CAS update failed (concurrent), shipmentNo={}", shipmentNo);
            // 重新查询状态
            Shipment refreshed = shipmentRepository.findByShipmentNo(shipmentNo).orElse(shipment);
            if (ShipmentStatus.DELIVERED.getCode().equals(refreshed.getStatus())) {
                return true; // 已被其他线程更新
            }
            throw new IllegalStateException("签收失败，请重试");
        }

        log.info("[ShipmentService] Shipment delivered, shipmentNo={}", shipmentNo);

        // 发布 ShipmentDelivered 事件
        publishShipmentDeliveredEvent(shipmentNo, shipment.getOrderNo(), waybill.getWaybillNo());

        return true;
    }

    /**
     * 根据订单号查询发货单
     */
    public Shipment getByOrderNo(String orderNo) {
        return shipmentRepository.findByOrderNo(orderNo).orElse(null);
    }

    /**
     * 根据发货单号查询
     */
    public Shipment getByShipmentNo(String shipmentNo) {
        return shipmentRepository.findByShipmentNo(shipmentNo).orElse(null);
    }

    /**
     * 生成发货单号
     */
    private String generateShipmentNo() {
        return "SH" + System.currentTimeMillis() + String.format("%04d", (int) (Math.random() * 10000));
    }

    /**
     * 发布发货单创建事件
     */
    private void publishShipmentCreatedEvent(String shipmentNo, String orderNo) {
        try {
            ShipmentCreatedEvent event = ShipmentCreatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .shipmentNo(shipmentNo)
                    .orderNo(orderNo)
                    .eventTime(LocalDateTime.now())
                    .traceId(TraceContext.getTraceId())
                    .version("1.0")
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            BaseEvent baseEvent = BaseEvent.builder()
                    .messageId(event.getEventId())
                    .businessKey(orderNo)
                    .traceId(event.getTraceId())
                    .eventType(TAG_SHIPMENT_CREATED)
                    .version("1.0")
                    .eventTime(event.getEventTime())
                    .source("fulfillment-service")
                    .payload(payload)
                    .build();

            String destination = FULFILLMENT_TOPIC + ":" + TAG_SHIPMENT_CREATED;
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(baseEvent).build());

            log.info("[ShipmentService] ShipmentCreatedEvent published, shipmentNo={}, orderNo={}",
                    shipmentNo, orderNo);
        } catch (Exception e) {
            log.error("[ShipmentService] Failed to publish ShipmentCreatedEvent, shipmentNo={}", shipmentNo, e);
        }
    }

    /**
     * 发布发货事件
     */
    private void publishShipmentShippedEvent(String shipmentNo, String orderNo, String waybillNo, String carrier) {
        try {
            ShipmentShippedEvent event = ShipmentShippedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .shipmentNo(shipmentNo)
                    .orderNo(orderNo)
                    .waybillNo(waybillNo)
                    .carrier(carrier)
                    .shippedAt(LocalDateTime.now())
                    .eventTime(LocalDateTime.now())
                    .traceId(TraceContext.getTraceId())
                    .version("1.0")
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            BaseEvent baseEvent = BaseEvent.builder()
                    .messageId(event.getEventId())
                    .businessKey(orderNo)
                    .traceId(event.getTraceId())
                    .eventType(TAG_SHIPMENT_SHIPPED)
                    .version("1.0")
                    .eventTime(event.getEventTime())
                    .source("fulfillment-service")
                    .payload(payload)
                    .build();

            String destination = FULFILLMENT_TOPIC + ":" + TAG_SHIPMENT_SHIPPED;
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(baseEvent).build());

            log.info("[ShipmentService] ShipmentShippedEvent published, shipmentNo={}, orderNo={}, waybillNo={}",
                    shipmentNo, orderNo, waybillNo);
        } catch (Exception e) {
            log.error("[ShipmentService] Failed to publish ShipmentShippedEvent, shipmentNo={}", shipmentNo, e);
        }
    }

    /**
     * 发布签收事件
     */
    private void publishShipmentDeliveredEvent(String shipmentNo, String orderNo, String waybillNo) {
        try {
            ShipmentDeliveredEvent event = ShipmentDeliveredEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .shipmentNo(shipmentNo)
                    .orderNo(orderNo)
                    .waybillNo(waybillNo)
                    .deliveredAt(LocalDateTime.now())
                    .eventTime(LocalDateTime.now())
                    .traceId(TraceContext.getTraceId())
                    .version("1.0")
                    .build();

            String payload = objectMapper.writeValueAsString(event);

            BaseEvent baseEvent = BaseEvent.builder()
                    .messageId(event.getEventId())
                    .businessKey(orderNo)
                    .traceId(event.getTraceId())
                    .eventType(TAG_SHIPMENT_DELIVERED)
                    .version("1.0")
                    .eventTime(event.getEventTime())
                    .source("fulfillment-service")
                    .payload(payload)
                    .build();

            String destination = FULFILLMENT_TOPIC + ":" + TAG_SHIPMENT_DELIVERED;
            rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(baseEvent).build());

            log.info("[ShipmentService] ShipmentDeliveredEvent published, shipmentNo={}, orderNo={}, waybillNo={}",
                    shipmentNo, orderNo, waybillNo);
        } catch (Exception e) {
            log.error("[ShipmentService] Failed to publish ShipmentDeliveredEvent, shipmentNo={}", shipmentNo, e);
        }
    }
}
