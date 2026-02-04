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

/**
 * SPU实体 (Standard Product Unit - 标准产品单元)
 * SPU是商品信息聚合的最小单位，如iPhone 15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("t_spu")
public class Spu extends BaseEntity {

    /**
     * SPU ID
     */
    @TableId(value = "spu_id", type = IdType.AUTO)
    private Long spuId;

    /**
     * SPU标题
     */
    private String title;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 品牌ID
     */
    private Long brandId;

    /**
     * 商品描述
     */
    private String description;

    /**
     * 状态: DRAFT-草稿, PENDING-待审核, PUBLISHED-已发布, OFFLINE-已下架
     */
    private String status;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}
