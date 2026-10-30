package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 订单服务启动类。
 * 放 com.ticket 包:扫描覆盖 com.ticket.order 域 + com.ticket.common 公共组件。
 * Feign:调 train-service(车次)/ticket-service(库存);
 * Scheduling:对账任务(超时补刀/补删凭证)。
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
