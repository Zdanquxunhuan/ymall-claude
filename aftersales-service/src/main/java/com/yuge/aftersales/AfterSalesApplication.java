package com.yuge.aftersales;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 售后服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.aftersales", "com.yuge.platform.infra"})
@MapperScan("com.yuge.aftersales.infrastructure.mapper")
@EnableScheduling
public class AfterSalesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfterSalesApplication.class, args);
    }
}
