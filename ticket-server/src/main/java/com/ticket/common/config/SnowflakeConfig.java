package com.ticket.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.ticket.common.id.SnowflakeIdGenerator;

@Configuration 
public class SnowflakeConfig {
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator( @Value("${ticket.snowflake.worker-id:1}") long workerId) {
        return new SnowflakeIdGenerator(workerId);
    }
    @Bean
    public IdentifierGenerator identifierGenerator(SnowflakeIdGenerator snowflakeIdGenerator) {
        return entity -> snowflakeIdGenerator.nextId();
    }

}
