package com.yuge.pricing;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定价服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.pricing", "com.yuge.platform.infra"})
@MapperScan("com.yuge.pricing.infrastructure.mapper")
@EnableScheduling
public class PricingApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingApplication.class, args);
    }
}
