package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 余票服务启动类(库存扣减核心所在)。
 * 放 com.ticket 包:扫描范围覆盖 com.ticket.ticket 业务域和 com.ticket.common 公共组件。
 * @EnableScheduling:库存续期任务(每天生成第31天库存)在本服务。
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
