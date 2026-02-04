package com.yuge.fulfillment.api.controller;

import com.yuge.fulfillment.api.dto.DeliverRequest;
import com.yuge.fulfillment.api.dto.ShipRequest;
import com.yuge.fulfillment.api.dto.ShipmentDTO;
import com.yuge.fulfillment.application.ShipmentService;
import com.yuge.fulfillment.domain.entity.Shipment;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.domain.enums.ShipmentStatus;
import com.yuge.fulfillment.infrastructure.repository.WaybillRepository;
import com.yuge.platform.infra.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 发货单控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final WaybillRepository waybillRepository;

    /**
     * 发货
     */
    @PostMapping("/ship")
    public Result<ShipmentDTO> ship(@Valid @RequestBody ShipRequest request) {
        log.info("[ShipmentController] Ship request, shipmentNo={}, waybillNo={}, carrier={}",
                request.getShipmentNo(), request.getWaybillNo(), request.getCarrier());

        try {
            shipmentService.ship(request.getShipmentNo(), request.getWaybillNo(), request.getCarrier());
            Shipment shipment = shipmentService.getByShipmentNo(request.getShipmentNo());
            return Result.success(toDTO(shipment));
        } catch (IllegalArgumentException e) {
            log.warn("[ShipmentController] Ship failed, error={}", e.getMessage());
            return Result.fail("SHIPMENT_NOT_FOUND", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("[ShipmentController] Ship failed, error={}", e.getMessage());
            return Result.fail("INVALID_STATUS", e.getMessage());
        }
    }

    /**
     * 签收
     */
    @PostMapping("/deliver")
    public Result<ShipmentDTO> deliver(@Valid @RequestBody DeliverRequest request) {
        log.info("[ShipmentController] Deliver request, shipmentNo={}", request.getShipmentNo());

        try {
            shipmentService.deliver(request.getShipmentNo());
            Shipment shipment = shipmentService.getByShipmentNo(request.getShipmentNo());
            return Result.success(toDTO(shipment));
        } catch (IllegalArgumentException e) {
            log.warn("[ShipmentController] Deliver failed, error={}", e.getMessage());
            return Result.fail("SHIPMENT_NOT_FOUND", e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("[ShipmentController] Deliver failed, error={}", e.getMessage());
            return Result.fail("INVALID_STATUS", e.getMessage());
        }
    }

    /**
     * 根据发货单号查询
     */
    @GetMapping("/{shipmentNo}")
    public Result<ShipmentDTO> getByShipmentNo(@PathVariable String shipmentNo) {
        Shipment shipment = shipmentService.getByShipmentNo(shipmentNo);
        if (shipment == null) {
            return Result.fail("SHIPMENT_NOT_FOUND", "发货单不存在: " + shipmentNo);
        }
        return Result.success(toDTO(shipment));
    }

    /**
     * 根据订单号查询
     */
    @GetMapping("/order/{orderNo}")
    public Result<ShipmentDTO> getByOrderNo(@PathVariable String orderNo) {
        Shipment shipment = shipmentService.getByOrderNo(orderNo);
        if (shipment == null) {
            return Result.fail("SHIPMENT_NOT_FOUND", "订单无发货单: " + orderNo);
        }
        return Result.success(toDTO(shipment));
    }

    /**
     * 转换为DTO
     */
    private ShipmentDTO toDTO(Shipment shipment) {
        ShipmentDTO dto = ShipmentDTO.builder()
                .shipmentNo(shipment.getShipmentNo())
                .orderNo(shipment.getOrderNo())
                .status(shipment.getStatus())
                .statusDesc(ShipmentStatus.of(shipment.getStatus()).getDesc())
                .shippedAt(shipment.getShippedAt())
                .deliveredAt(shipment.getDeliveredAt())
                .createdAt(shipment.getCreatedAt())
                .build();

        // 查询运单信息
        waybillRepository.findByShipmentNo(shipment.getShipmentNo())
                .ifPresent(waybill -> {
                    dto.setWaybillNo(waybill.getWaybillNo());
                    dto.setCarrier(waybill.getCarrier());
                });

        return dto;
    }
}
