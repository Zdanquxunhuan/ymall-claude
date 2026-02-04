package com.yuge.fulfillment.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yuge.fulfillment.domain.entity.LogisticsTrack;
import com.yuge.fulfillment.infrastructure.mapper.LogisticsTrackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 物流轨迹仓储
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LogisticsTrackRepository {

    private final LogisticsTrackMapper logisticsTrackMapper;

    /**
     * 保存轨迹（支持乱序去重）
     * 利用唯一约束去重，INSERT IGNORE
     *
     * @param track 轨迹
     * @return true-插入成功，false-重复被忽略
     */
    public boolean saveIgnoreDuplicate(LogisticsTrack track) {
        if (track.getId() == null) {
            track.setId(IdWorker.getId());
        }
        int inserted = logisticsTrackMapper.insertIgnore(track);
        if (inserted == 0) {
            log.debug("[LogisticsTrackRepository] Duplicate track ignored, waybillNo={}, nodeTime={}, nodeCode={}",
                    track.getWaybillNo(), track.getNodeTime(), track.getNodeCode());
        }
        return inserted > 0;
    }

    /**
     * 批量保存轨迹（支持乱序去重）
     *
     * @param tracks 轨迹列表
     * @return 实际插入数量
     */
    public int batchSaveIgnoreDuplicate(List<LogisticsTrack> tracks) {
        int insertedCount = 0;
        for (LogisticsTrack track : tracks) {
            if (saveIgnoreDuplicate(track)) {
                insertedCount++;
            }
        }
        return insertedCount;
    }

    /**
     * 根据运单号查询轨迹（按节点时间排序）
     *
     * @param waybillNo 运单号
     * @return 轨迹列表（按node_time升序）
     */
    public List<LogisticsTrack> findByWaybillNoOrderByNodeTime(String waybillNo) {
        return logisticsTrackMapper.selectList(
                new LambdaQueryWrapper<LogisticsTrack>()
                        .eq(LogisticsTrack::getWaybillNo, waybillNo)
                        .orderByAsc(LogisticsTrack::getNodeTime)
        );
    }
}
