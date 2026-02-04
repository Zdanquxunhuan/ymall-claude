package com.yuge.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 订单服务启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.yuge.platform.infra", "com.yuge.order"})
@MapperScan("com.yuge.order.infrastructure.mapper")
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
