package com.yuge.promotion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 促销服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.promotion", "com.yuge.platform.infra"})
@MapperScan("com.yuge.promotion.infrastructure.mapper")
@EnableScheduling
public class PromotionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionApplication.class, args);
    }
}
