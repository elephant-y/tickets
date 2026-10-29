package com.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 *
 * 注意:类必须放在 com.ticket 包(而不是 com.ticket.user)——
 * @SpringBootApplication 的扫描范围 = 本类所在包及子包,
 * 放这里才能同时扫描到 com.ticket.user 业务域和 com.ticket.common 公共组件。
 */
@SpringBootApplication
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
