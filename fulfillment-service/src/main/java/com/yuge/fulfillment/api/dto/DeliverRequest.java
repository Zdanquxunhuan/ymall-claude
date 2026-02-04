package com.yuge.fulfillment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 签收请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliverRequest {

    /**
     * 发货单号
     */
    @NotBlank(message = "发货单号不能为空")
    private String shipmentNo;
}
