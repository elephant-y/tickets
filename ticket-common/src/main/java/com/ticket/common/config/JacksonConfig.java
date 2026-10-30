package com.ticket.common.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * Jackson 全局配置:所有 Long 序列化为字符串。
 *
 * 为什么必须(经典坑):雪花 ID 是 64 位长整型(约 1.09e18),
 * 而 JavaScript 的 number 是双精度浮点,只能精确表示 ±2^53(约 9e15)内的整数 ——
 * JSON 里的数字 ID 被前端解析时会四舍五入(实测:109557954365116416 → ...420,差 4),
 * 前端拿着错的 ID 回传,后端 404。
 * 行业标准做法:Long 一律以字符串进出前端,字符串无精度问题;
 * 后端接收时 Spring 自动把字符串转回 Long,业务代码零改动。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
