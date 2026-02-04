package com.yuge.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.order.domain.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox事件Mapper
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 查询待处理的事件（NEW 或 RETRY 状态，且到达重试时间）
     * 使用 FOR UPDATE SKIP LOCKED 实现分布式锁，支持多实例并行处理
     */
    @Select("SELECT * FROM t_outbox_event " +
            "WHERE status IN ('NEW', 'RETRY') " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY created_at ASC " +
            "LIMIT #{limit} " +
            "FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> selectProcessableEventsForUpdate(@Param("limit") int limit);

    /**
     * 查询待处理的事件（不加锁，用于监控）
     */
    @Select("SELECT * FROM t_outbox_event " +
            "WHERE status IN ('NEW', 'RETRY') " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY created_at ASC " +
            "LIMIT #{limit}")
    List<OutboxEvent> selectProcessableEvents(@Param("limit") int limit);

    /**
     * 标记事件为已发送（使用乐观锁）
     */
    @Update("UPDATE t_outbox_event SET " +
            "status = 'SENT', " +
            "sent_at = NOW(), " +
            "updated_at = NOW(), " +
            "version = version + 1 " +
            "WHERE event_id = #{eventId} " +
            "AND status IN ('NEW', 'RETRY') " +
            "AND version = #{version}")
    int markAsSent(@Param("eventId") String eventId, @Param("version") Integer version);

    /**
     * 标记事件为重试状态（指数退避）
     */
    @Update("UPDATE t_outbox_event SET " +
            "status = 'RETRY', " +
            "retry_count = retry_count + 1, " +
            "next_retry_at = #{nextRetryAt}, " +
            "last_error = #{lastError}, " +
            "updated_at = NOW(), " +
            "version = version + 1 " +
            "WHERE event_id = #{eventId} " +
            "AND version = #{version}")
    int markAsRetry(@Param("eventId") String eventId, 
                    @Param("nextRetryAt") LocalDateTime nextRetryAt,
                    @Param("lastError") String lastError,
                    @Param("version") Integer version);

    /**
     * 标记事件为死信状态
     */
    @Update("UPDATE t_outbox_event SET " +
            "status = 'DEAD', " +
            "last_error = #{lastError}, " +
            "updated_at = NOW(), " +
            "version = version + 1 " +
            "WHERE event_id = #{eventId} " +
            "AND version = #{version}")
    int markAsDead(@Param("eventId") String eventId, 
                   @Param("lastError") String lastError,
                   @Param("version") Integer version);

    /**
     * 根据事件ID查询
     */
    @Select("SELECT * FROM t_outbox_event WHERE event_id = #{eventId}")
    OutboxEvent selectByEventId(@Param("eventId") String eventId);

    /**
     * 统计各状态的事件数量
     */
    @Select("SELECT status, COUNT(*) as cnt FROM t_outbox_event GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();

    /**
     * 查询死信事件
     */
    @Select("SELECT * FROM t_outbox_event WHERE status = 'DEAD' ORDER BY updated_at DESC LIMIT #{limit}")
    List<OutboxEvent> selectDeadEvents(@Param("limit") int limit);
}
