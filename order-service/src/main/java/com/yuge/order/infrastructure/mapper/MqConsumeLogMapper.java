package com.yuge.order.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.order.domain.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MQ消费日志Mapper
 */
@Mapper
public interface MqConsumeLogMapper extends BaseMapper<MqConsumeLog> {

    /**
     * 根据事件ID和消费者组查询
     */
    @Select("SELECT * FROM t_mq_consume_log WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    MqConsumeLog selectByEventIdAndGroup(@Param("eventId") String eventId, 
                                          @Param("consumerGroup") String consumerGroup);

    /**
     * 标记为成功
     */
    @Update("UPDATE t_mq_consume_log SET status = 'SUCCESS', result = #{result}, " +
            "cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup} AND status = 'PROCESSING'")
    int markAsSuccess(@Param("eventId") String eventId, 
                      @Param("consumerGroup") String consumerGroup,
                      @Param("result") String result,
                      @Param("costMs") Long costMs);

    /**
     * 标记为失败
     */
    @Update("UPDATE t_mq_consume_log SET status = 'FAILED', result = #{result}, " +
            "cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup} AND status = 'PROCESSING'")
    int markAsFailed(@Param("eventId") String eventId, 
                     @Param("consumerGroup") String consumerGroup,
                     @Param("result") String result,
                     @Param("costMs") Long costMs);

    /**
     * 标记为已忽略（乱序消息）
     */
    @Update("UPDATE t_mq_consume_log SET status = 'IGNORED', result = #{result}, " +
            "ignored_reason = #{ignoredReason}, cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup} AND status = 'PROCESSING'")
    int markAsIgnored(@Param("eventId") String eventId,
                      @Param("consumerGroup") String consumerGroup,
                      @Param("result") String result,
                      @Param("ignoredReason") String ignoredReason,
                      @Param("costMs") Long costMs);
}
