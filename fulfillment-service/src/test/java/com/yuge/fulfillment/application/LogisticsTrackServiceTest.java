package com.yuge.fulfillment.application;

import com.yuge.fulfillment.domain.entity.LogisticsTrack;
import com.yuge.fulfillment.domain.entity.Waybill;
import com.yuge.fulfillment.infrastructure.repository.LogisticsTrackRepository;
import com.yuge.fulfillment.infrastructure.repository.WaybillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 物流轨迹服务测试
 */
@ExtendWith(MockitoExtension.class)
class LogisticsTrackServiceTest {

    @Mock
    private LogisticsTrackRepository logisticsTrackRepository;

    @Mock
    private WaybillRepository waybillRepository;

    @InjectMocks
    private LogisticsTrackService logisticsTrackService;

    private static final String WAYBILL_NO = "SF1234567890";
    private static final String SHIPMENT_NO = "SH1234567890";

    @BeforeEach
    void setUp() {
        // Setup common mocks if needed
    }

    @Test
    @DisplayName("上报轨迹成功 - 新轨迹")
    void shouldReportTrackSuccessfully() {
        // Arrange
        LocalDateTime nodeTime = LocalDateTime.now();
        String nodeCode = "COLLECTED";
        String content = "快件已揽收";

        Waybill waybill = Waybill.builder()
                .waybillNo(WAYBILL_NO)
                .shipmentNo(SHIPMENT_NO)
                .carrier("顺丰速运")
                .build();

        when(waybillRepository.findByWaybillNo(WAYBILL_NO)).thenReturn(Optional.of(waybill));
        when(logisticsTrackRepository.saveIgnoreDuplicate(any(LogisticsTrack.class))).thenReturn(true);

        // Act
        boolean result = logisticsTrackService.reportTrack(WAYBILL_NO, nodeTime, nodeCode, content);

        // Assert
        assertTrue(result);
        verify(waybillRepository).findByWaybillNo(WAYBILL_NO);
        verify(logisticsTrackRepository).saveIgnoreDuplicate(any(LogisticsTrack.class));
    }

    @Test
    @DisplayName("上报轨迹 - 重复轨迹被忽略")
    void shouldIgnoreDuplicateTrack() {
        // Arrange
        LocalDateTime nodeTime = LocalDateTime.now();
        String nodeCode = "COLLECTED";
        String content = "快件已揽收";

        Waybill waybill = Waybill.builder()
                .waybillNo(WAYBILL_NO)
                .shipmentNo(SHIPMENT_NO)
                .carrier("顺丰速运")
                .build();

        when(waybillRepository.findByWaybillNo(WAYBILL_NO)).thenReturn(Optional.of(waybill));
        when(logisticsTrackRepository.saveIgnoreDuplicate(any(LogisticsTrack.class))).thenReturn(false);

        // Act
        boolean result = logisticsTrackService.reportTrack(WAYBILL_NO, nodeTime, nodeCode, content);

        // Assert
        assertFalse(result);
        verify(logisticsTrackRepository).saveIgnoreDuplicate(any(LogisticsTrack.class));
    }

    @Test
    @DisplayName("上报轨迹失败 - 运单不存在")
    void shouldThrowExceptionWhenWaybillNotFound() {
        // Arrange
        LocalDateTime nodeTime = LocalDateTime.now();
        String nodeCode = "COLLECTED";
        String content = "快件已揽收";

        when(waybillRepository.findByWaybillNo(WAYBILL_NO)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                logisticsTrackService.reportTrack(WAYBILL_NO, nodeTime, nodeCode, content));

        assertEquals("运单不存在: " + WAYBILL_NO, exception.getMessage());
        verify(logisticsTrackRepository, never()).saveIgnoreDuplicate(any());
    }

    @Test
    @DisplayName("批量上报轨迹 - 部分成功")
    void shouldBatchReportTracksPartially() {
        // Arrange
        List<LogisticsTrack> tracks = Arrays.asList(
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(LocalDateTime.now().minusHours(2))
                        .nodeCode("COLLECTED")
                        .content("快件已揽收")
                        .build(),
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(LocalDateTime.now().minusHours(1))
                        .nodeCode("IN_TRANSIT")
                        .content("快件已到达转运中心")
                        .build(),
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(LocalDateTime.now())
                        .nodeCode("DELIVERED")
                        .content("快件已签收")
                        .build()
        );

        // 模拟第二条是重复的
        when(logisticsTrackRepository.batchSaveIgnoreDuplicate(tracks)).thenReturn(2);

        // Act
        int insertedCount = logisticsTrackService.batchReportTracks(tracks);

        // Assert
        assertEquals(2, insertedCount);
    }

    @Test
    @DisplayName("查询轨迹 - 按时间排序")
    void shouldGetTracksOrderedByNodeTime() {
        // Arrange
        LocalDateTime time1 = LocalDateTime.now().minusHours(2);
        LocalDateTime time2 = LocalDateTime.now().minusHours(1);
        LocalDateTime time3 = LocalDateTime.now();

        List<LogisticsTrack> tracks = Arrays.asList(
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(time1)
                        .nodeCode("COLLECTED")
                        .content("快件已揽收")
                        .build(),
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(time2)
                        .nodeCode("IN_TRANSIT")
                        .content("快件已到达转运中心")
                        .build(),
                LogisticsTrack.builder()
                        .waybillNo(WAYBILL_NO)
                        .nodeTime(time3)
                        .nodeCode("DELIVERED")
                        .content("快件已签收")
                        .build()
        );

        when(logisticsTrackRepository.findByWaybillNoOrderByNodeTime(WAYBILL_NO)).thenReturn(tracks);

        // Act
        List<LogisticsTrack> result = logisticsTrackService.getTracksByWaybillNo(WAYBILL_NO);

        // Assert
        assertEquals(3, result.size());
        assertEquals("COLLECTED", result.get(0).getNodeCode());
        assertEquals("IN_TRANSIT", result.get(1).getNodeCode());
        assertEquals("DELIVERED", result.get(2).getNodeCode());
    }

    @Test
    @DisplayName("乱序上报轨迹 - 验证去重机制")
    void shouldHandleOutOfOrderTracks() {
        // Arrange - 模拟乱序上报：先上报签收，再上报揽收
        LocalDateTime collectTime = LocalDateTime.now().minusHours(2);
        LocalDateTime deliverTime = LocalDateTime.now();

        Waybill waybill = Waybill.builder()
                .waybillNo(WAYBILL_NO)
                .shipmentNo(SHIPMENT_NO)
                .carrier("顺丰速运")
                .build();

        when(waybillRepository.findByWaybillNo(WAYBILL_NO)).thenReturn(Optional.of(waybill));
        when(logisticsTrackRepository.saveIgnoreDuplicate(any(LogisticsTrack.class))).thenReturn(true);

        // Act - 先上报签收
        boolean result1 = logisticsTrackService.reportTrack(WAYBILL_NO, deliverTime, "DELIVERED", "快件已签收");
        // 再上报揽收（乱序）
        boolean result2 = logisticsTrackService.reportTrack(WAYBILL_NO, collectTime, "COLLECTED", "快件已揽收");

        // Assert - 两条都应该成功插入
        assertTrue(result1);
        assertTrue(result2);
        verify(logisticsTrackRepository, times(2)).saveIgnoreDuplicate(any(LogisticsTrack.class));
    }
}
