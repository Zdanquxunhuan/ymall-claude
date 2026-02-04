package com.yuge.cart.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车合并日志实体
 * 记录游客车与登录车合并的历史
 */
@Data
@TableName("t_cart_merge_log")
public class CartMergeLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 游客ID
     */
    private String anonId;

    /**
     * 合并策略: QTY_ADD(数量累加), LATEST_WIN(以最新为准)
     */
    private String mergeStrategy;

    /**
     * 合并前游客车快照（JSON）
     */
    private String anonCartSnapshot;

    /**
     * 合并前用户车快照（JSON）
     */
    private String userCartSnapshot;

    /**
     * 合并后用户车快照（JSON）
     */
    private String mergedCartSnapshot;

    /**
     * 合并的SKU数量
     */
    private Integer mergedSkuCount;

    /**
     * 冲突的SKU数量（同SKU存在于两个车中）
     */
    private Integer conflictSkuCount;

    /**
     * 合并耗时（毫秒）
     */
    private Long mergeTimeMs;

    /**
     * 备注
     */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
