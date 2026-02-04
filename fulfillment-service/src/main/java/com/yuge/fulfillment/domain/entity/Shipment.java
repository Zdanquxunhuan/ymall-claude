package com.yuge.fulfillment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 发货单实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_shipment")
public class Shipment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 发货单号
     */
    private String shipmentNo;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 状态: CREATED-已创建, SHIPPED-已发货, DELIVERED-已签收
     */
    private String status;

    /**
     * 发货时间
     */
    private LocalDateTime shippedAt;

    /**
     * 签收时间
     */
    private LocalDateTime deliveredAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;

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
}
