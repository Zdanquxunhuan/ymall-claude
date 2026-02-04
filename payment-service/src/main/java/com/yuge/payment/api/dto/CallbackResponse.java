package com.yuge.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回调处理响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackResponse {

    /**
     * 处理结果: PROCESSED/IGNORED/FAILED
     */
    private String result;

    /**
     * 处理消息
     */
    private String message;

    /**
     * 支付单号
     */
    private String payNo;

    /**
     * 当前支付状态
     */
    private String currentStatus;

    public static CallbackResponse success(String payNo, String status) {
        return CallbackResponse.builder()
                .result("PROCESSED")
                .message("回调处理成功")
                .payNo(payNo)
                .currentStatus(status)
                .build();
    }

    public static CallbackResponse ignored(String payNo, String status, String reason) {
        return CallbackResponse.builder()
                .result("IGNORED")
                .message(reason)
                .payNo(payNo)
                .currentStatus(status)
                .build();
    }

    public static CallbackResponse failed(String payNo, String reason) {
        return CallbackResponse.builder()
                .result("FAILED")
                .message(reason)
                .payNo(payNo)
                .build();
    }
}
