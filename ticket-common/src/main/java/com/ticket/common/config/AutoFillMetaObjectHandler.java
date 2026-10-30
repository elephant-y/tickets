package com.ticket.common.config;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;

/**
 * 时间字段自动填充(时区修复"腿2"):
 * 之前 createTime 靠 DB 的 DEFAULT CURRENT_TIMESTAMP 兜底,而该默认值求值时用的是
 * MySQL 服务端时区(部署环境说了算),与 JVM 时区不一致时跨时钟比较会出错
 * (曾导致对账任务把新订单误判为 8 小时前的单子而取消)。
 * 改为 insert/update 前由 Java 显式写入 —— 业务时钟唯一来源是 JVM,与部署环境解耦。
 * DB 端 DEFAULT 保留作手工 SQL 的兜底,两条腿互补。
 * 注意:update(null, LambdaUpdateWrapper) 不带实体,不会触发 updateFill,
 * 那种写法要在 wrapper 里显式 set updateTime(见 OrderService.markPaid)。
 */
@Component
public class AutoFillMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        // strict*Fill:字段为 null 才填,代码里显式 set 过的不覆盖(补偿回填历史时间时用)
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
