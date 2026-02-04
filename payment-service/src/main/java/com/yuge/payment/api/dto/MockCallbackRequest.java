package com.yuge.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟支付回调请求
 */
@Data
public class MockCallbackRequest {

    /**
     * 支付单号
     */
    @NotBlank(message = "支付单号不能为空")
    private String payNo;

    /**
     * 回调状态: SUCCESS/FAILED
     */
    @NotBlank(message = "回调状态不能为空")
    private String callbackStatus;

    /**
     * 渠道交易号（模拟）
     */
    private String channelTradeNo;

    /**
     * 签名（必须）
     */
    @NotBlank(message = "签名不能为空")
    private String signature;

    /**
     * 时间戳（用于签名验证）
     */
    @NotBlank(message = "时间戳不能为空")
    private String timestamp;

    /**
     * 随机字符串（用于签名验证）
     */
    @NotBlank(message = "随机字符串不能为空")
    private String nonce;
}
