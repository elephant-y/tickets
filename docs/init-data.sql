-- =============================================================
-- 购票系统初始化数据脚本
-- 环境: MySQL 8.x | 需先执行 schema.sql 完成建表
-- 本地执行: docker exec -i mysql mysql -uroot -p123456 < docs/init-data.sql
-- 说明: 幂等(先清空后插入),可重复执行
-- =============================================================

USE `ticket`;

-- 清空(按引用顺序反删,保证重跑得到干净状态)
DELETE FROM `train_seat_stock`;
DELETE FROM `train_station`;
DELETE FROM `train`;
DELETE FROM `station`;
DELETE FROM `seat_type`;

-- -------------------------------------------------------------
-- 1. 站点
-- 为什么用固定 id: 种子数据是受控的,固定 id 让经停表/车次表引用直观可读;
--    运行时产生的数据才由应用雪花生成器负责,两者互不冲突
-- -------------------------------------------------------------
INSERT INTO `station` (`id`, `name`, `city`) VALUES
(1, '北京南', '北京'),
(2, '济南西', '济南'),
(3, '南京南', '南京'),
(4, '上海虹桥', '上海');

-- -------------------------------------------------------------
-- 2. 座位类型(price_rate 为相对二等座的倍率)
-- -------------------------------------------------------------
INSERT INTO `seat_type` (`id`, `type_name`, `price_rate`) VALUES
(11, '二等座', 1.00),
(12, '一等座', 1.60),
(13, '商务座', 3.00);

-- -------------------------------------------------------------
-- 3. 车次
-- -------------------------------------------------------------
INSERT INTO `train` (`id`, `train_no`, `train_type`, `start_station_id`, `end_station_id`, `depart_time`, `arrive_time`, `status`) VALUES
(101, 'G101', 1, 1, 4, '07:00:00', '11:30:00', 1),
(201, 'G201', 1, 1, 4, '09:00:00', '13:10:00', 1);

-- -------------------------------------------------------------
-- 4. 车次经停
--    mileage 为距始发站累计里程(公里),区间里程 = 两站相减,用于算票价
--    G101: 北京南-济南西-南京南-上海虹桥(4 站 → C(4,2)=6 个区间)
--    G201: 北京南-济南西-上海虹桥(3 站 → C(3,2)=3 个区间)
-- -------------------------------------------------------------
INSERT INTO `train_station` (`id`, `train_id`, `station_id`, `station_no`, `arrive_time`, `depart_time`, `mileage`) VALUES
-- G101
(1101, 101, 1, 1, NULL,        '07:00:00', 0),
(1102, 101, 2, 2, '08:25:00', '08:30:00', 406),
(1103, 101, 3, 3, '10:15:00', '10:20:00', 1018),
(1104, 101, 4, 4, '11:30:00', NULL,       1313),
-- G201
(2101, 201, 1, 1, NULL,        '09:00:00', 0),
(2102, 201, 2, 2, '10:25:00', '10:28:00', 406),
(2103, 201, 4, 3, '13:10:00', NULL,       1313);

-- -------------------------------------------------------------
-- 5. 区间库存(核心)
--    每个车次 × 未来30天 × 所有区间(经停表自连接) × 座位类型 各一行
--    总座位数按座位类型模拟编组: 二等座100 / 一等座24 / 商务座12
--    行数校验: (6 + 3) 区间 × 3 类型 × 30 天 = 810 行
-- -------------------------------------------------------------
INSERT INTO `train_seat_stock`
  (`id`, `train_id`, `train_date`, `seat_type_id`, `from_station_no`, `to_station_no`, `total_seat`, `sold_seat`, `version`)
WITH RECURSIVE days AS (
  -- 锚点:今天;递归:每次 +1 天,直到第 30 天
  SELECT CURDATE() AS d
  UNION ALL
  SELECT DATE_ADD(d, INTERVAL 1 DAY) FROM days WHERE d < DATE_ADD(CURDATE(), INTERVAL 29 DAY)
)
SELECT UUID_SHORT(),          -- id: 无应用雪花生成器时用内置 UUID_SHORT 占位(同为64位趋势递增整数)
       ts1.train_id,
       days.d,
       st.id,
       ts1.station_no,
       ts2.station_no,
       CASE st.id
         WHEN 11 THEN 100
         WHEN 12 THEN 24
         WHEN 13 THEN 12
       END,
       0,
       0
FROM train_station ts1
JOIN train_station ts2
  ON ts1.train_id = ts2.train_id AND ts1.station_no < ts2.station_no   -- 自连接生成 from<to 的全部合法区间
CROSS JOIN seat_type st
CROSS JOIN days;

-- -------------------------------------------------------------
-- 6. 自查统计(期望:station=4 seat_type=3 train=2 train_station=7 train_seat_stock=810)
-- -------------------------------------------------------------
SELECT 'station'          AS tbl, COUNT(*) AS cnt FROM station
UNION ALL SELECT 'seat_type',      COUNT(*) FROM seat_type
UNION ALL SELECT 'train',          COUNT(*) FROM train
UNION ALL SELECT 'train_station',  COUNT(*) FROM train_station
UNION ALL SELECT 'train_seat_stock', COUNT(*) FROM train_seat_stock;
