package com.yuge.fulfillment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuge.fulfillment.domain.entity.Shipment;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.domain.enums.ShipmentStatus;
import com.yuge.fulfillment.infrastructure.repository.ShipmentRepository;
import com.yuge.fulfillment.infrastructure.repository.WaybillRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 发货单服务测试
 */
@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private WaybillRepository waybillRepository;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ShipmentService shipmentService;

    private static final String ORDER_NO = "ORD1234567890";
    private static final String SHIPMENT_NO = "SH1234567890";
    private static final String WAYBILL_NO = "SF1234567890";
    private static final String CARRIER = "顺丰速运";
    private static final String EVENT_ID = "evt-123";

    @BeforeEach
    void setUp() throws Exception {
        // Mock ObjectMapper for event serialization
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    @DisplayName("创建发货单成功")
    void shouldCreateShipmentSuccessfully() {
        // Arrange
        when(shipmentRepository.existsByOrderNo(ORDER_NO)).thenReturn(false);
        doNothing().when(shipmentRepository).save(any(Shipment.class));

        // Act
        String shipmentNo = shipmentService.createShipment(ORDER_NO, EVENT_ID);

        // Assert
        assertNotNull(shipmentNo);
        assertTrue(shipmentNo.startsWith("SH"));
        verify(shipmentRepository).existsByOrderNo(ORDER_NO);
        verify(shipmentRepository).save(any(Shipment.class));
    }

    @Test
    @DisplayName("创建发货单失败 - 订单已有发货单")
    void shouldThrowExceptionWhenShipmentExists() {
        // Arrange
        Shipment existingShipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.CREATED.getCode())
                .build();

        when(shipmentRepository.existsByOrderNo(ORDER_NO)).thenReturn(true);
        when(shipmentRepository.findByOrderNo(ORDER_NO)).thenReturn(Optional.of(existingShipment));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                shipmentService.createShipment(ORDER_NO, EVENT_ID));

        assertTrue(exception.getMessage().contains("订单已有发货单"));
        verify(shipmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("发货成功")
    void shouldShipSuccessfully() {
        // Arrange
        Shipment shipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.CREATED.getCode())
                .build();

        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));
        when(waybillRepository.existsByShipmentNo(SHIPMENT_NO)).thenReturn(false);
        doNothing().when(waybillRepository).save(any(Waybill.class));
        when(shipmentRepository.casUpdateStatusToShipped(SHIPMENT_NO)).thenReturn(true);

        // Act
        boolean result = shipmentService.ship(SHIPMENT_NO, WAYBILL_NO, CARRIER);

        // Assert
        assertTrue(result);
        verify(waybillRepository).save(any(Waybill.class));
        verify(shipmentRepository).casUpdateStatusToShipped(SHIPMENT_NO);
    }

    @Test
    @DisplayName("发货幂等 - 已发货状态")
    void shouldReturnTrueWhenAlreadyShipped() {
        // Arrange
        Shipment shipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.SHIPPED.getCode())
                .build();

        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));

        // Act
        boolean result = shipmentService.ship(SHIPMENT_NO, WAYBILL_NO, CARRIER);

        // Assert
        assertTrue(result);
        verify(shipmentRepository, never()).casUpdateStatusToShipped(anyString());
    }

    @Test
    @DisplayName("发货失败 - 发货单不存在")
    void shouldThrowExceptionWhenShipmentNotFoundForShip() {
        // Arrange
        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                shipmentService.ship(SHIPMENT_NO, WAYBILL_NO, CARRIER));

        assertEquals("发货单不存在: " + SHIPMENT_NO, exception.getMessage());
    }

    @Test
    @DisplayName("签收成功")
    void shouldDeliverSuccessfully() {
        // Arrange
        Shipment shipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.SHIPPED.getCode())
                .build();

        Waybill waybill = Waybill.builder()
                .waybillNo(WAYBILL_NO)
                .shipmentNo(SHIPMENT_NO)
                .carrier(CARRIER)
                .build();

        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));
        when(waybillRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(waybill));
        when(shipmentRepository.casUpdateStatusToDelivered(SHIPMENT_NO)).thenReturn(true);

        // Act
        boolean result = shipmentService.deliver(SHIPMENT_NO);

        // Assert
        assertTrue(result);
        verify(shipmentRepository).casUpdateStatusToDelivered(SHIPMENT_NO);
    }

    @Test
    @DisplayName("签收幂等 - 已签收状态")
    void shouldReturnTrueWhenAlreadyDelivered() {
        // Arrange
        Shipment shipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.DELIVERED.getCode())
                .build();

        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));

        // Act
        boolean result = shipmentService.deliver(SHIPMENT_NO);

        // Assert
        assertTrue(result);
        verify(shipmentRepository, never()).casUpdateStatusToDelivered(anyString());
    }

    @Test
    @DisplayName("签收失败 - 状态不允许")
    void shouldThrowExceptionWhenStatusNotAllowDeliver() {
        // Arrange
        Shipment shipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.CREATED.getCode())
                .build();

        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(shipment));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                shipmentService.deliver(SHIPMENT_NO));

        assertTrue(exception.getMessage().contains("当前状态不允许签收"));
    }

    @Test
    @DisplayName("发货单状态流转 - CREATED -> SHIPPED -> DELIVERED")
    void shouldFollowCorrectStateTransition() {
        // Arrange - CREATED state
        Shipment createdShipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.CREATED.getCode())
                .build();

        Shipment shippedShipment = Shipment.builder()
                .shipmentNo(SHIPMENT_NO)
                .orderNo(ORDER_NO)
                .status(ShipmentStatus.SHIPPED.getCode())
                .build();

        Waybill waybill = Waybill.builder()
                .waybillNo(WAYBILL_NO)
                .shipmentNo(SHIPMENT_NO)
                .carrier(CARRIER)
                .build();

        // Step 1: Ship
        when(shipmentRepository.findByShipmentNo(SHIPMENT_NO))
                .thenReturn(Optional.of(createdShipment))
                .thenReturn(Optional.of(shippedShipment));
        when(waybillRepository.existsByShipmentNo(SHIPMENT_NO)).thenReturn(false);
        doNothing().when(waybillRepository).save(any(Waybill.class));
        when(shipmentRepository.casUpdateStatusToShipped(SHIPMENT_NO)).thenReturn(true);

        boolean shipResult = shipmentService.ship(SHIPMENT_NO, WAYBILL_NO, CARRIER);
        assertTrue(shipResult);

        // Step 2: Deliver
        when(waybillRepository.findByShipmentNo(SHIPMENT_NO)).thenReturn(Optional.of(waybill));
        when(shipmentRepository.casUpdateStatusToDelivered(SHIPMENT_NO)).thenReturn(true);

        boolean deliverResult = shipmentService.deliver(SHIPMENT_NO);
        assertTrue(deliverResult);

        // Verify state transitions
        verify(shipmentRepository).casUpdateStatusToShipped(SHIPMENT_NO);
        verify(shipmentRepository).casUpdateStatusToDelivered(SHIPMENT_NO);
    }
}
