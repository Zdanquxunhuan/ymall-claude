package com.yuge.order.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细实体
 */
@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 购买数量
     */
    private Integer qty;

    /**
     * 商品标题快照
     */
    private String titleSnapshot;

    /**
     * 商品单价快照
     */
    private BigDecimal priceSnapshot;

    /**
     * 优惠金额（分摊）
     */
    private BigDecimal discountAmount;

    /**
     * 实付金额（分摊后）
     */
    private BigDecimal payableAmount;

    /**
     * 促销信息快照(JSON)
     */
    private String promoSnapshotJson;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
