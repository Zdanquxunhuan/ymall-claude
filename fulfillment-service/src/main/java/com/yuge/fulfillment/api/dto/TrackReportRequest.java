package com.yuge.fulfillment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 轨迹上报请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackReportRequest {

    /**
     * 运单号
     */
    @NotBlank(message = "运单号不能为空")
    private String waybillNo;

    /**
     * 节点时间
     */
    @NotNull(message = "节点时间不能为空")
    private LocalDateTime nodeTime;

    /**
     * 节点编码
     */
    @NotBlank(message = "节点编码不能为空")
    private String nodeCode;

    /**
     * 轨迹内容
     */
    @NotBlank(message = "轨迹内容不能为空")
    private String content;
}
