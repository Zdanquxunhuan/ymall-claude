package com.yuge.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 搜索服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.search", "com.yuge.platform.infra"})
@EnableScheduling
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
