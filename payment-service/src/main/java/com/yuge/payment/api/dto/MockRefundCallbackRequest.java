package com.yuge.payment.api.dto;

import lombok.Data;

/**
 * 模拟退款回调请求
 */
@Data
public class MockRefundCallbackRequest {

    /**
     * 退款单号
     */
    private String refundNo;

    /**
     * 渠道退款流水号
     */
    private String channelRefundNo;

    /**
     * 回调状态: SUCCESS/FAILED
     */
    private String callbackStatus;

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 随机数
     */
    private String nonce;

    /**
     * 签名
     */
    private String signature;
}
