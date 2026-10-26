package com.ticket.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 网关启动类。
 * 网关本质就是一个「换了依赖」的 Spring Boot 应用:
 * 不写业务 Controller,所有能力来自 spring-cloud-starter-gateway 的自动配置。
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
