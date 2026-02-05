package com.yuge.aftersales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 售后状态流转审计实体
 */
@Data
@TableName("t_after_sale_state_flow")
public class AfterSaleStateFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 售后单号
     */
    private String asNo;

    /**
     * 原状态
     */
    private String fromStatus;

    /**
     * 目标状态
     */
    private String toStatus;

    /**
     * 触发事件
     */
    private String event;

    /**
     * 事件ID
     */
    private String eventId;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 构建状态流转记录
     */
    public static AfterSaleStateFlow build(String asNo, String fromStatus, String toStatus,
                                            String event, String eventId, String operator,
                                            String traceId, String remark) {
        AfterSaleStateFlow flow = new AfterSaleStateFlow();
        flow.setAsNo(asNo);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setEvent(event);
        flow.setEventId(eventId);
        flow.setOperator(operator);
        flow.setTraceId(traceId);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        return flow;
    }
}
