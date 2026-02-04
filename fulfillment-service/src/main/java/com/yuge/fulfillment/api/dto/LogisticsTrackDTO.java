package com.yuge.fulfillment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物流轨迹DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogisticsTrackDTO {

    /**
     * 运单号
     */
    private String waybillNo;

    /**
     * 节点时间
     */
    private LocalDateTime nodeTime;

    /**
     * 节点编码
     */
    private String nodeCode;

    /**
     * 轨迹内容
     */
    private String content;
}
