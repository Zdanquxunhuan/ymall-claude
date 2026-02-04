package com.yuge.cart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 购物车服务启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.yuge.cart", "com.yuge.platform.infra"})
@MapperScan("com.yuge.cart.infrastructure.repository")
public class CartApplication {

    public static void main(String[] args) {
        SpringApplication.run(CartApplication.class, args);
    }
}
