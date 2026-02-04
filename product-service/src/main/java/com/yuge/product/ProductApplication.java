package com.yuge.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 商品服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.product", "com.yuge.platform.infra"})
@MapperScan("com.yuge.product.infrastructure.mapper")
@EnableScheduling
public class ProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductApplication.class, args);
    }
}
