package com.yuge.fulfillment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 物流轨迹实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_logistics_track")
public class LogisticsTrack {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 运单号
     */
    private String waybillNo;

    /**
     * 节点时间
     */
    private LocalDateTime nodeTime;

    /**
     * 节点编码
     */
    private String nodeCode;

    /**
     * 轨迹内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
