package com.yuge.fulfillment.api.controller;

import com.yuge.fulfillment.api.dto.LogisticsTrackDTO;
import com.yuge.fulfillment.api.dto.TrackReportRequest;
import com.yuge.fulfillment.application.LogisticsTrackService;
import com.yuge.fulfillment.domain.entity.LogisticsTrack;
import com.yuge.platform.infra.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 物流轨迹控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/logistics")
@RequiredArgsConstructor
public class LogisticsTrackController {

    private final LogisticsTrackService logisticsTrackService;

    /**
     * 上报物流轨迹（支持乱序去重）
     */
    @PostMapping("/track")
    public Result<Boolean> reportTrack(@Valid @RequestBody TrackReportRequest request) {
        log.info("[LogisticsTrackController] Report track, waybillNo={}, nodeTime={}, nodeCode={}",
                request.getWaybillNo(), request.getNodeTime(), request.getNodeCode());

        try {
            boolean inserted = logisticsTrackService.reportTrack(
                    request.getWaybillNo(),
                    request.getNodeTime(),
                    request.getNodeCode(),
                    request.getContent()
            );
            return Result.success(inserted);
        } catch (IllegalArgumentException e) {
            log.warn("[LogisticsTrackController] Report track failed, error={}", e.getMessage());
            return Result.fail("WAYBILL_NOT_FOUND", e.getMessage());
        }
    }

    /**
     * 批量上报物流轨迹（支持乱序去重）
     */
    @PostMapping("/tracks/batch")
    public Result<Integer> batchReportTracks(@Valid @RequestBody List<TrackReportRequest> requests) {
        log.info("[LogisticsTrackController] Batch report tracks, count={}", requests.size());

        List<LogisticsTrack> tracks = requests.stream()
                .map(req -> LogisticsTrack.builder()
                        .waybillNo(req.getWaybillNo())
                        .nodeTime(req.getNodeTime())
                        .nodeCode(req.getNodeCode())
                        .content(req.getContent())
                        .build())
                .collect(Collectors.toList());

        int insertedCount = logisticsTrackService.batchReportTracks(tracks);
        return Result.success(insertedCount);
    }

    /**
     * 根据运单号查询轨迹（按时间排序）
     */
    @GetMapping("/waybill/{waybillNo}/tracks")
    public Result<List<LogisticsTrackDTO>> getTracksByWaybillNo(@PathVariable String waybillNo) {
        List<LogisticsTrack> tracks = logisticsTrackService.getTracksByWaybillNo(waybillNo);
        List<LogisticsTrackDTO> dtos = tracks.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return Result.success(dtos);
    }

    /**
     * 根据发货单号查询轨迹（按时间排序）
     */
    @GetMapping("/shipment/{shipmentNo}/tracks")
    public Result<List<LogisticsTrackDTO>> getTracksByShipmentNo(@PathVariable String shipmentNo) {
        try {
            List<LogisticsTrack> tracks = logisticsTrackService.getTracksByShipmentNo(shipmentNo);
            List<LogisticsTrackDTO> dtos = tracks.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
            return Result.success(dtos);
        } catch (IllegalArgumentException e) {
            log.warn("[LogisticsTrackController] Get tracks failed, error={}", e.getMessage());
            return Result.fail("WAYBILL_NOT_FOUND", e.getMessage());
        }
    }

    /**
     * 转换为DTO
     */
    private LogisticsTrackDTO toDTO(LogisticsTrack track) {
        return LogisticsTrackDTO.builder()
                .waybillNo(track.getWaybillNo())
                .nodeTime(track.getNodeTime())
                .nodeCode(track.getNodeCode())
                .content(track.getContent())
                .build();
    }
}
