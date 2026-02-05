package com.yuge.search.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 搜索服务配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    /**
     * 索引类型: memory, elasticsearch
     */
    private String indexType = "memory";

    /**
     * Elasticsearch 配置
     */
    private ElasticsearchConfig elasticsearch = new ElasticsearchConfig();

    /**
     * 索引重建配置
     */
    private RebuildConfig rebuild = new RebuildConfig();

    @Data
    public static class ElasticsearchConfig {
        /**
         * 是否启用 ES
         */
        private boolean enabled = false;

        /**
         * ES 主机地址
         */
        private String hosts = "localhost:9200";

        /**
         * 用户名
         */
        private String username;

        /**
         * 密码
         */
        private String password;

        /**
         * 索引名称
         */
        private String indexName = "product_index";

        /**
         * 连接超时（毫秒）
         */
        private int connectTimeout = 5000;

        /**
         * 读取超时（毫秒）
         */
        private int socketTimeout = 30000;
    }

    @Data
    public static class RebuildConfig {
        /**
         * 是否启用索引重建任务
         */
        private boolean enabled = false;

        /**
         * 批量大小
         */
        private int batchSize = 100;

        /**
         * 重建间隔（cron表达式）
         */
        private String cron = "0 0 3 * * ?";
    }
}
