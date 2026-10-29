package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 购票系统启动类。
 *
 * 阶段一~四:单体应用 —— 先纵后横,在单体里跑通「查询→下单→扣库存→支付」主链路,
 * 亲身体会直接查库/行锁的瓶颈之后,阶段五起再拆微服务、上消息队列。
 */
@SpringBootApplication
@EnableScheduling   // 定时任务总开关(对账任务/库存续期用)
@EnableFeignClients // 服务间调用(调 train-service 的内部接口)
public class TicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
