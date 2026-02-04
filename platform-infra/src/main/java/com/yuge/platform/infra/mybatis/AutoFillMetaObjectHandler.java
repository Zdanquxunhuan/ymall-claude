package com.yuge.platform.infra.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器
 * 自动填充 createdAt, updatedAt, createdBy, updatedBy 字段
 */
@Slf4j
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("开始插入填充...");
        
        // 填充创建时间
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        // 填充更新时间
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        // 填充创建人（从上下文获取，这里先用默认值）
        this.strictInsertFill(metaObject, "createdBy", String.class, getCurrentUser());
        // 填充更新人
        this.strictInsertFill(metaObject, "updatedBy", String.class, getCurrentUser());
        // 填充版本号
        this.strictInsertFill(metaObject, "version", Integer.class, 1);
        // 填充删除标记
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("开始更新填充...");
        
        // 填充更新时间
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        // 填充更新人
        this.strictUpdateFill(metaObject, "updatedBy", String.class, getCurrentUser());
    }

    /**
     * 获取当前用户
     * TODO: 从 SecurityContext 或 UserContext 获取
     */
    private String getCurrentUser() {
        // 这里可以从 ThreadLocal 或 Spring Security 上下文获取当前用户
        // 暂时返回默认值
        return "system";
    }
}
