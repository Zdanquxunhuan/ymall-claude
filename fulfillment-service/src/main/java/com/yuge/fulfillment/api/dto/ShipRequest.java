package com.yuge.fulfillment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 发货请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipRequest {

    /**
     * 发货单号
     */
    @NotBlank(message = "发货单号不能为空")
    private String shipmentNo;

    /**
     * 运单号
     */
    @NotBlank(message = "运单号不能为空")
    private String waybillNo;

    /**
     * 承运商
     */
    @NotBlank(message = "承运商不能为空")
    private String carrier;
}
