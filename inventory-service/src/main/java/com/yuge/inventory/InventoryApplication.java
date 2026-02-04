package com.yuge.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 库存服务启动类
 */
@SpringBootApplication(scanBasePackages = {"com.yuge.inventory", "com.yuge.platform.infra"})
@MapperScan("com.yuge.inventory.infrastructure.mapper")
@EnableScheduling
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
