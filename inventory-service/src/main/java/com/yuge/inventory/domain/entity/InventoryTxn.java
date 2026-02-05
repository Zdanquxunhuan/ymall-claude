package com.yuge.inventory.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yuge.inventory.domain.enums.TxnReason;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存流水实体
 * 记录所有库存变动，用于审计和问题排查
 */
@Data
@TableName("t_inventory_txn")
public class InventoryTxn implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 流水ID（UUID）
     */
    private String txnId;

    /**
     * 关联订单号
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
     * 可用库存变化量（正数增加，负数减少）
     */
    private Integer deltaAvailable;

    /**
     * 预留库存变化量（正数增加，负数减少）
     */
    private Integer deltaReserved;

    /**
     * 变更后可用库存
     */
    private Integer availableAfter;

    /**
     * 变更后预留库存
     */
    private Integer reservedAfter;

    /**
     * 变动原因: RESERVE-预留, CONFIRM-确认, RELEASE-释放, ADJUST-调整
     */
    private String reason;

    /**
     * 备注
     */
    private String remark;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 获取原因枚举
     */
    public TxnReason getReasonEnum() {
        return TxnReason.of(this.reason);
    }

    /**
     * 设置原因枚举
     */
    public void setReasonEnum(TxnReason reason) {
        this.reason = reason.getCode();
    }

    /**
     * 构建预留流水
     */
    public static InventoryTxn buildReserveTxn(String txnId, String orderNo, Long skuId, Long warehouseId,
                                                int qty, int availableAfter, int reservedAfter, String traceId) {
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(txnId);
        txn.setOrderNo(orderNo);
        txn.setSkuId(skuId);
        txn.setWarehouseId(warehouseId);
        txn.setDeltaAvailable(-qty);
        txn.setDeltaReserved(qty);
        txn.setAvailableAfter(availableAfter);
        txn.setReservedAfter(reservedAfter);
        txn.setReason(TxnReason.RESERVE.getCode());
        txn.setRemark("订单预留库存");
        txn.setTraceId(traceId);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    /**
     * 构建确认流水
     */
    public static InventoryTxn buildConfirmTxn(String txnId, String orderNo, Long skuId, Long warehouseId,
                                                int qty, int availableAfter, int reservedAfter, String traceId) {
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(txnId);
        txn.setOrderNo(orderNo);
        txn.setSkuId(skuId);
        txn.setWarehouseId(warehouseId);
        txn.setDeltaAvailable(0);
        txn.setDeltaReserved(-qty);
        txn.setAvailableAfter(availableAfter);
        txn.setReservedAfter(reservedAfter);
        txn.setReason(TxnReason.CONFIRM.getCode());
        txn.setRemark("订单确认扣减库存");
        txn.setTraceId(traceId);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    /**
     * 构建释放流水
     */
    public static InventoryTxn buildReleaseTxn(String txnId, String orderNo, Long skuId, Long warehouseId,
                                                int qty, int availableAfter, int reservedAfter, 
                                                String remark, String traceId) {
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(txnId);
        txn.setOrderNo(orderNo);
        txn.setSkuId(skuId);
        txn.setWarehouseId(warehouseId);
        txn.setDeltaAvailable(qty);
        txn.setDeltaReserved(-qty);
        txn.setAvailableAfter(availableAfter);
        txn.setReservedAfter(reservedAfter);
        txn.setReason(TxnReason.RELEASE.getCode());
        txn.setRemark(remark != null ? remark : "释放预留库存");
        txn.setTraceId(traceId);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    /**
     * 构建退款回补流水
     */
    public static InventoryTxn buildRefundRestoreTxn(String txnId, String orderNo, Long skuId, Long warehouseId,
                                                      int qty, int availableAfter, int reservedAfter,
                                                      String asNo, String traceId) {
        InventoryTxn txn = new InventoryTxn();
        txn.setTxnId(txnId);
        txn.setOrderNo(orderNo);
        txn.setSkuId(skuId);
        txn.setWarehouseId(warehouseId);
        txn.setDeltaAvailable(qty);
        txn.setDeltaReserved(0);
        txn.setAvailableAfter(availableAfter);
        txn.setReservedAfter(reservedAfter);
        txn.setReason(TxnReason.REFUND_RESTORE.getCode());
        txn.setRemark("售后退款回补库存，售后单号: " + asNo);
        txn.setTraceId(traceId);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }
}
