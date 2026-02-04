package com.yuge.fulfillment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 履约服务启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.yuge.fulfillment", "com.yuge.platform.infra"})
@MapperScan("com.yuge.fulfillment.infrastructure.mapper")
@EnableScheduling
public class FulfillmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(FulfillmentApplication.class, args);
    }
}
