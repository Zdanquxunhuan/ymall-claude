package com.yuge.aftersales.api.dto;

import lombok.Data;

/**
 * 审批售后请求
 */
@Data
public class ApproveAfterSaleRequest {

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 审批人
     */
    private String approvedBy;
}
