package com.yuge.product.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuge.product.domain.entity.Outbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Outbox Mapper接口
 */
@Mapper
public interface OutboxMapper extends BaseMapper<Outbox> {

    /**
     * 查询待发送的消息（带行锁）
     */
    @Select("SELECT * FROM t_outbox WHERE status = 'PENDING' " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY created_at ASC LIMIT #{limit} FOR UPDATE SKIP LOCKED")
    List<Outbox> selectPendingWithLock(@Param("limit") int limit);
}
