package com.yuge.aftersales.api.dto;

import lombok.Data;

/**
 * 拒绝售后请求
 */
@Data
public class RejectAfterSaleRequest {

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 审批人
     */
    private String approvedBy;
}
