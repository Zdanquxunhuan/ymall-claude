package com.yuge.inventory.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.inventory.domain.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

/**
 * MQ消费日志Mapper
 */
@Mapper
public interface MqConsumeLogMapper extends BaseMapper<MqConsumeLog> {

    /**
     * 根据事件ID和消费者组查询
     */
    @Select("SELECT * FROM t_mq_consume_log WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    Optional<MqConsumeLog> findByEventIdAndConsumerGroup(@Param("eventId") String eventId,
                                                          @Param("consumerGroup") String consumerGroup);

    /**
     * 更新消费状态为成功
     */
    @Update("UPDATE t_mq_consume_log SET " +
            "status = 'SUCCESS', " +
            "result = #{result}, " +
            "cost_ms = #{costMs}, " +
            "updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int markSuccess(@Param("eventId") String eventId,
                    @Param("consumerGroup") String consumerGroup,
                    @Param("result") String result,
                    @Param("costMs") long costMs);

    /**
     * 更新消费状态为失败
     */
    @Update("UPDATE t_mq_consume_log SET " +
            "status = 'FAILED', " +
            "result = #{result}, " +
            "cost_ms = #{costMs}, " +
            "updated_at = NOW() " +
            "WHERE event_id = #{eventId} AND consumer_group = #{consumerGroup}")
    int markFailed(@Param("eventId") String eventId,
                   @Param("consumerGroup") String consumerGroup,
                   @Param("result") String result,
                   @Param("costMs") long costMs);
}
