package com.yuge.aftersales.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.yuge.aftersales.domain.enums.AfterSaleStatus;
import com.yuge.aftersales.domain.enums.AfterSaleType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单实体
 */
@Data
@TableName("t_after_sale")
public class AfterSale {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 售后类型: REFUND/RETURN_REFUND
     */
    private String type;

    /**
     * 售后状态: APPLIED/APPROVED/REFUNDING/REFUNDED/REJECTED/CANCELED
     */
    private String status;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 退款总金额
     */
    private BigDecimal refundAmount;

    /**
     * 退款单号（关联payment-service）
     */
    private String refundNo;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;

    /**
     * 审批人
     */
    private String approvedBy;

    /**
     * 退款完成时间
     */
    private LocalDateTime refundedAt;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除
     */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 获取状态枚举
     */
    public AfterSaleStatus getStatusEnum() {
        return AfterSaleStatus.of(this.status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(AfterSaleStatus status) {
        this.status = status.getCode();
    }

    /**
     * 获取类型枚举
     */
    public AfterSaleType getTypeEnum() {
        return AfterSaleType.of(this.type);
    }

    /**
     * 设置类型枚举
     */
    public void setTypeEnum(AfterSaleType type) {
        this.type = type.getCode();
    }

    /**
     * 判断是否可以取消
     */
    public boolean canCancel() {
        return getStatusEnum().canCancel();
    }

    /**
     * 判断是否可以审批
     */
    public boolean canApprove() {
        return getStatusEnum().canApprove();
    }

    /**
     * 判断是否可以拒绝
     */
    public boolean canReject() {
        return getStatusEnum().canReject();
    }

    /**
     * 判断是否为终态
     */
    public boolean isTerminal() {
        return getStatusEnum().isTerminal();
    }
}
