package com.yuge.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.yuge.inventory.domain.enums.ReservationStatus;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存预留实体
 * 记录订单对库存的预留情况，用于幂等和超时释放
 */
@Data
@TableName("t_inventory_reservation")
public class InventoryReservation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 预留数量
     */
    private Integer qty;

    /**
     * 状态: RESERD-已预留, CONFIRMED-已确认, RELEASED-已释放
     */
    private String status;

    /**
     * 预留过期时间
     */
    private LocalDateTime expireAt;

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
     * 获取状态枚举
     */
    public ReservationStatus getStatusEnum() {
        return ReservationStatus.of(this.status);
    }

    /**
     * 设置状态枚举
     */
    public void setStatusEnum(ReservationStatus status) {
        this.status = status.getCode();
    }

    /**
     * 是否已预留状态
     */
    public boolean isReserved() {
        return ReservationStatus.RESERVED.getCode().equals(this.status);
    }

    /**
     * 是否已确认状态
     */
    public boolean isConfirmed() {
        return ReservationStatus.CONFIRMED.getCode().equals(this.status);
    }

    /**
     * 是否已释放状态
     */
    public boolean isReleased() {
        return ReservationStatus.RELEASED.getCode().equals(this.status);
    }

    /**
     * 是否已过期
     */
    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }
}
