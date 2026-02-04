package com.yuge.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存实体
 * 记录每个SKU在每个仓库的可用库存和预留库存
 */
@Data
@TableName("t_inventory")
public class Inventory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * SKU ID
     */
    private Long skuId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 可用库存数量
     */
    private Integer availableQty;

    /**
     * 预留库存数量（已被订单占用但未确认）
     */
    private Integer reservedQty;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记 0-未删除 1-已删除
     */
    @TableLogic
    private Integer deleted;

    /**
     * 获取总库存（可用 + 预留）
     */
    public Integer getTotalQty() {
        return (availableQty != null ? availableQty : 0) + (reservedQty != null ? reservedQty : 0);
    }

    /**
     * 检查是否有足够的可用库存
     */
    public boolean hasAvailable(int qty) {
        return availableQty != null && availableQty >= qty;
    }
}
