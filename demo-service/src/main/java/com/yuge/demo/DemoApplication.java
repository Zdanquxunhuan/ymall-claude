package com.yuge.demo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Demo服务启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.yuge.platform.infra", "com.yuge.demo"})
@MapperScan("com.yuge.demo.infrastructure.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
