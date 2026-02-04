package com.yuge.fulfillment.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.fulfillment.domain.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MQ消费日志Mapper
 */
@Mapper
public interface MqConsumeLogMapper extends BaseMapper<MqConsumeLog> {

    /**
     * 尝试插入消费记录（幂等）
     *
     * @param log 消费日志
     * @return 插入行数
     */
    @Insert("INSERT IGNORE INTO t_fulfillment_mq_consume_log " +
            "(id, event_id, consumer_group, topic, tags, biz_key, status, created_at, updated_at) " +
            "VALUES (#{log.id}, #{log.eventId}, #{log.consumerGroup}, #{log.topic}, #{log.tags}, " +
            "#{log.bizKey}, #{log.status}, NOW(), NOW())")
    int insertIgnore(@Param("log") MqConsumeLog log);

    /**
     * 更新消费状态为成功
     *
     * @param eventId       事件ID
     * @param consumerGroup 消费者组
     * @param resultMsg     结果消息
     * @param costMs        耗时
     * @return 更新行数
     */
    @Update("UPDATE t_fulfillment_mq_consume_log SET status = 'SUCCESS', result_msg = #{resultMsg}, " +
            "cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int markSuccess(@Param("eventId") String eventId,
                    @Param("consumerGroup") String consumerGroup,
                    @Param("resultMsg") String resultMsg,
                    @Param("costMs") Long costMs);

    /**
     * 更新消费状态为失败
     *
     * @param eventId       事件ID
     * @param consumerGroup 消费者组
     * @param resultMsg     结果消息
     * @param costMs        耗时
     * @return 更新行数
     */
    @Update("UPDATE t_fulfillment_mq_consume_log SET status = 'FAILED', result_msg = #{resultMsg}, " +
            "cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int markFailed(@Param("eventId") String eventId,
                   @Param("consumerGroup") String consumerGroup,
                   @Param("resultMsg") String resultMsg,
                   @Param("costMs") Long costMs);

    /**
     * 更新消费状态为已忽略
     *
     * @param eventId       事件ID
     * @param consumerGroup 消费者组
     * @param resultMsg     结果消息
     * @param ignoredReason 忽略原因
     * @param costMs        耗时
     * @return 更新行数
     */
    @Update("UPDATE t_fulfillment_mq_consume_log SET status = 'IGNORED', result_msg = #{resultMsg}, " +
            "ignored_reason = #{ignoredReason}, cost_ms = #{costMs}, updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int markIgnored(@Param("eventId") String eventId,
                    @Param("consumerGroup") String consumerGroup,
                    @Param("resultMsg") String resultMsg,
                    @Param("ignoredReason") String ignoredReason,
                    @Param("costMs") Long costMs);
}
