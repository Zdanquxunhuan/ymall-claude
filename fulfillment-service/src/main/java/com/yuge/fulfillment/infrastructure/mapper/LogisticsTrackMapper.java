package com.yuge.fulfillment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.fulfillment.domain.entity.LogisticsTrack;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物流轨迹Mapper
 */
@Mapper
public interface LogisticsTrackMapper extends BaseMapper<LogisticsTrack> {

    /**
     * 插入轨迹（忽略重复）
     * 利用唯一约束去重，支持乱序写入
     *
     * @param track 轨迹
     * @return 插入行数（0表示重复被忽略）
     */
    @Insert("INSERT IGNORE INTO t_logistics_track (id, waybill_no, node_time, node_code, content, created_at) " +
            "VALUES (#{track.id}, #{track.waybillNo}, #{track.nodeTime}, #{track.nodeCode}, #{track.content}, NOW())")
    int insertIgnore(@Param("track") LogisticsTrack track);
}
