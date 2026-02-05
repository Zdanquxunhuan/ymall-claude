package com.yuge.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退款回调响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundCallbackResponse {

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 处理结果: SUCCESS/IGNORED/FAILED
     */
    private String result;

    /**
     * 当前状态
     */
    private String currentStatus;

    /**
     * 消息
     */
    private String message;

    public static RefundCallbackResponse success(String refundNo, String currentStatus) {
        return RefundCallbackResponse.builder()
                .refundNo(refundNo)
                .result("SUCCESS")
                .currentStatus(currentStatus)
                .message("处理成功")
                .build();
    }

    public static RefundCallbackResponse ignored(String refundNo, String currentStatus, String message) {
        return RefundCallbackResponse.builder()
                .refundNo(refundNo)
                .result("IGNORED")
                .currentStatus(currentStatus)
                .message(message)
                .build();
    }

    public static RefundCallbackResponse failed(String refundNo, String message) {
        return RefundCallbackResponse.builder()
                .refundNo(refundNo)
                .result("FAILED")
                .message(message)
                .build();
    }
}
