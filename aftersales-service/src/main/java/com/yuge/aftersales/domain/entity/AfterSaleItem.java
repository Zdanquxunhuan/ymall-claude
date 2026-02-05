package com.yuge.aftersales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后单明细实体
 */
@Data
@TableName("t_after_sale_item")
public class AfterSaleItem {

    @TableId(type = IdType.AUTO)
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
     * 订单明细ID
     */
    private Long orderItemId;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 退款数量
     */
    private Integer qty;

    /**
     * 退款金额（分摊后）
     */
    private BigDecimal refundAmount;

    /**
     * 原价快照
     */
    private BigDecimal originalPrice;

    /**
     * 实付金额快照
     */
    private BigDecimal payableAmount;

    /**
     * 促销信息快照(JSON)
     */
    private String promoSnapshotJson;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
