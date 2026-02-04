package com.yuge.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.yuge.platform.infra.mybatis.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU实体 (Stock Keeping Unit - 库存量单位)
 * SKU是库存进出计量的基本单元，如iPhone 15 黑色 256GB
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_sku")
public class Sku extends BaseEntity {

    /**
     * SKU ID
     */
    @TableId(value = "sku_id", type = IdType.AUTO)
    private Long skuId;

    /**
     * 所属SPU ID
     */
    private Long spuId;

    /**
     * SKU标题
     */
    private String title;

    /**
     * 销售属性JSON，如{"颜色":"黑色","容量":"256GB"}
     */
    private String attrsJson;

    /**
     * 销售价格
     */
    private BigDecimal price;

    /**
     * 原价
     */
    private BigDecimal originalPrice;

    /**
     * SKU编码
     */
    private String skuCode;

    /**
     * 条形码
     */
    private String barCode;

    /**
     * 重量(kg)
     */
    private BigDecimal weight;

    /**
     * 状态: DRAFT-草稿, PENDING-待审核, PUBLISHED-已发布, OFFLINE-已下架
     */
    private String status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
