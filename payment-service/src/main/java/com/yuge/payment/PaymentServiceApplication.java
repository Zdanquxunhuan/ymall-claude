package com.yuge.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.yuge.payment", "com.yuge.platform.infra"})
@MapperScan("com.yuge.payment.infrastructure.mapper")
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
