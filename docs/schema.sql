-- =============================================================
-- 购票系统(ticket)数据库初始化脚本
-- 环境: MySQL 8.x | 引擎 InnoDB | 字符集 utf8mb4
-- 本地执行: docker exec -i ticket-mysql mysql -uroot -p123456 < schema.sql
-- 说明: 开发期可重复执行(先 DROP 后 CREATE);
--       引入 Flyway 后转为版本化迁移脚本(V1__init.sql 等)
-- =============================================================

CREATE DATABASE IF NOT EXISTS `ticket`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `ticket`;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `username`    VARCHAR(32) NOT NULL COMMENT '用户名',
  `password`    VARCHAR(64) NOT NULL COMMENT '密码,BCrypt密文(明文禁止入库)',
  `phone`       VARCHAR(11) DEFAULT NULL COMMENT '手机号',
  `id_card`     VARCHAR(18) DEFAULT NULL COMMENT '实名证件号',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -------------------------------------------------------------
-- 2. 站点表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `station`;
CREATE TABLE `station` (
  `id`   BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `name` VARCHAR(32) NOT NULL COMMENT '站名,如北京南',
  `city` VARCHAR(32) DEFAULT NULL COMMENT '所在城市',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点表';

-- -------------------------------------------------------------
-- 3. 车次表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `train`;
CREATE TABLE `train` (
  `id`               BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `train_no`         VARCHAR(16) NOT NULL COMMENT '车次号,如G123',
  `train_type`       TINYINT     NOT NULL COMMENT '车型:1高铁 2动车 3普速',
  `start_station_id` BIGINT      NOT NULL COMMENT '始发站ID',
  `end_station_id`   BIGINT      NOT NULL COMMENT '终到站ID',
  `depart_time`      TIME        NOT NULL COMMENT '始发时间',
  `arrive_time`      TIME        NOT NULL COMMENT '终到时间',
  `status`           TINYINT     NOT NULL DEFAULT 1 COMMENT '状态:1开行 0停运',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_train_no` (`train_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次表';

-- -------------------------------------------------------------
-- 4. 座位类型表(字典表)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `seat_type`;
CREATE TABLE `seat_type` (
  `id`         BIGINT       NOT NULL COMMENT '主键,雪花ID',
  `type_name`  VARCHAR(16)  NOT NULL COMMENT '类型名:二等座/一等座/商务座',
  `price_rate` DECIMAL(4,2) NOT NULL DEFAULT 1.00 COMMENT '相对基准价倍率',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位类型表';

-- -------------------------------------------------------------
-- 5. 车次经停表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `train_station`;
CREATE TABLE `train_station` (
  `id`           BIGINT NOT NULL COMMENT '主键,雪花ID',
  `train_id`     BIGINT NOT NULL COMMENT '车次ID',
  `station_id`   BIGINT NOT NULL COMMENT '站点ID',
  `station_no`   INT    NOT NULL COMMENT '站序,从1开始(车次内有序,用于区间运算)',
  `arrive_time`  TIME   DEFAULT NULL COMMENT '到站时间(始发站为空)',
  `depart_time`  TIME   DEFAULT NULL COMMENT '发车时间(终到站为空)',
  `mileage`      INT    NOT NULL COMMENT '距始发站里程(公里),区间里程=两站相减',
  PRIMARY KEY (`id`),
  KEY `idx_train_station` (`train_id`, `station_no`),
  KEY `idx_station_train` (`station_id`, `train_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次经停表';

-- -------------------------------------------------------------
-- 6. 余票表(系统核心,面试重点)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `train_seat_stock`;
CREATE TABLE `train_seat_stock` (
  `id`              BIGINT NOT NULL COMMENT '主键,雪花ID',
  `train_id`        BIGINT NOT NULL COMMENT '车次ID',
  `train_date`      DATE   NOT NULL COMMENT '开行日期',
  `seat_type_id`    BIGINT NOT NULL COMMENT '座位类型ID',
  `from_station_no` INT    NOT NULL COMMENT '起始站序',
  `to_station_no`   INT    NOT NULL COMMENT '到达站序',
  `total_seat`      INT    NOT NULL COMMENT '该区间票额(配置,简化为总座位数)',
  `sold_seat`       INT    NOT NULL DEFAULT 0 COMMENT '已售计数(事实),余票=total-seat为派生值不落库',
  `version`         INT    NOT NULL DEFAULT 0 COMMENT '乐观锁版本号,DB兜底扣减用',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock` (`train_id`, `train_date`, `seat_type_id`, `from_station_no`, `to_station_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车次每日区间余票表';

-- -------------------------------------------------------------
-- 7. 订单表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
  `id`              BIGINT        NOT NULL COMMENT '主键,雪花ID',
  `order_no`        VARCHAR(32)   NOT NULL COMMENT '业务订单号(对用户展示)',
  `user_id`         BIGINT        NOT NULL COMMENT '下单用户ID',
  `train_id`        BIGINT        NOT NULL COMMENT '车次ID',
  `train_no`        VARCHAR(16)   NOT NULL COMMENT '冗余车次号,列表展示免JOIN',
  `train_date`      DATE          NOT NULL COMMENT '乘车日期',
  `from_station_id` BIGINT        NOT NULL COMMENT '出发站ID',
  `to_station_id`   BIGINT        NOT NULL COMMENT '到达站ID',
  `seat_type_id`    BIGINT        NOT NULL COMMENT '座位类型ID',
  `ticket_count`    INT           NOT NULL COMMENT '票数1~5',
  `total_amount`    DECIMAL(10,2) NOT NULL COMMENT '订单金额(元),禁止float/double',
  `status`          TINYINT       NOT NULL COMMENT '状态:10待支付 20已支付 30已出票 40已取消 50已退票',
  `pay_time`        DATETIME      DEFAULT NULL COMMENT '支付时间',
  `cancel_time`     DATETIME      DEFAULT NULL COMMENT '取消时间',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_status` (`user_id`, `status`, `create_time`),
  KEY `idx_train_date` (`train_id`, `train_date`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- -------------------------------------------------------------
-- 8. 订单明细表(乘车人,1:N)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `order_item`;
CREATE TABLE `order_item` (
  `id`             BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `order_id`       BIGINT      NOT NULL COMMENT '订单ID',
  `passenger_name` VARCHAR(32) NOT NULL COMMENT '乘车人姓名',
  `id_card`        VARCHAR(18) NOT NULL COMMENT '乘车人证件号',
  `seat_no`        VARCHAR(8)  DEFAULT NULL COMMENT '座位号,出票后回填',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细-乘车人';

-- -------------------------------------------------------------
-- 9. 占座凭证表(防重复购票:同一乘车人同车次同日期仅一张)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `seat_hold`;
CREATE TABLE `seat_hold` (
  `id`         BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `order_id`   BIGINT      NOT NULL COMMENT '关联订单ID',
  `id_card`    VARCHAR(18) NOT NULL COMMENT '乘车人证件号',
  `train_id`   BIGINT      NOT NULL COMMENT '车次ID',
  `train_date` DATE        NOT NULL COMMENT '乘车日期',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hold` (`id_card`, `train_id`, `train_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='占座凭证表:占座插入,取消/退票删除';

-- -------------------------------------------------------------
-- 10. 常用乘车人表(账号下储存的乘车人,下单时勾选)
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `passenger`;
CREATE TABLE `passenger` (
  `id`          BIGINT      NOT NULL COMMENT '主键,雪花ID',
  `user_id`     BIGINT      NOT NULL COMMENT '所属账号ID',
  `name`        VARCHAR(32) NOT NULL COMMENT '乘车人姓名',
  `id_card`     VARCHAR(18) NOT NULL COMMENT '证件号',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_card` (`user_id`, `id_card`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用乘车人表:同一账号同证件仅一条,删除不影响已购车票';
