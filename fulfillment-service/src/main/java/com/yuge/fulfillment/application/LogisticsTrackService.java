package com.yuge.fulfillment.application;

import com.yuge.fulfillment.domain.entity.LogisticsTrack;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.infrastructure.repository.LogisticsTrackRepository;
import com.yuge.fulfillment.infrastructure.repository.WaybillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物流轨迹服务
 * 
 * 支持乱序写入和去重
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogisticsTrackService {

    private final LogisticsTrackRepository logisticsTrackRepository;
    private final WaybillRepository waybillRepository;

    /**
     * 上报物流轨迹（支持乱序去重）
     * 
     * 利用数据库唯一约束 (waybill_no, node_time, node_code) 实现去重
     * INSERT IGNORE 保证乱序写入时不会报错
     *
     * @param waybillNo 运单号
     * @param nodeTime  节点时间
     * @param nodeCode  节点编码
     * @param content   轨迹内容
     * @return true-新增成功，false-重复被忽略
     */
    public boolean reportTrack(String waybillNo, LocalDateTime nodeTime, String nodeCode, String content) {
        // 验证运单是否存在
        if (!waybillRepository.findByWaybillNo(waybillNo).isPresent()) {
            log.warn("[LogisticsTrackService] Waybill not found, waybillNo={}", waybillNo);
            throw new IllegalArgumentException("运单不存在: " + waybillNo);
        }

        LogisticsTrack track = LogisticsTrack.builder()
                .waybillNo(waybillNo)
                .nodeTime(nodeTime)
                .nodeCode(nodeCode)
                .content(content)
                .build();

        boolean inserted = logisticsTrackRepository.saveIgnoreDuplicate(track);

        if (inserted) {
            log.info("[LogisticsTrackService] Track reported, waybillNo={}, nodeTime={}, nodeCode={}",
                    waybillNo, nodeTime, nodeCode);
        } else {
            log.debug("[LogisticsTrackService] Duplicate track ignored, waybillNo={}, nodeTime={}, nodeCode={}",
                    waybillNo, nodeTime, nodeCode);
        }

        return inserted;
    }

    /**
     * 批量上报物流轨迹（支持乱序去重）
     *
     * @param tracks 轨迹列表
     * @return 实际新增数量
     */
    public int batchReportTracks(List<LogisticsTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }

        int insertedCount = logisticsTrackRepository.batchSaveIgnoreDuplicate(tracks);
        log.info("[LogisticsTrackService] Batch tracks reported, total={}, inserted={}",
                tracks.size(), insertedCount);

        return insertedCount;
    }

    /**
     * 查询物流轨迹（按节点时间排序）
     *
     * @param waybillNo 运单号
     * @return 轨迹列表（按node_time升序）
     */
    public List<LogisticsTrack> getTracksByWaybillNo(String waybillNo) {
        return logisticsTrackRepository.findByWaybillNoOrderByNodeTime(waybillNo);
    }

    /**
     * 根据发货单号查询物流轨迹
     *
     * @param shipmentNo 发货单号
     * @return 轨迹列表（按node_time升序）
     */
    public List<LogisticsTrack> getTracksByShipmentNo(String shipmentNo) {
        Waybill waybill = waybillRepository.findByShipmentNo(shipmentNo)
                .orElseThrow(() -> new IllegalArgumentException("发货单无运单: " + shipmentNo));

        return getTracksByWaybillNo(waybill.getWaybillNo());
    }
}
