# 阶段五:RocketMQ 削峰

> 目标:下单从「同步即时」改造成「异步排队」——请求进 MQ,消费者按序处理,用户拿排队凭证轮询结果。
> 核心命题:**放票瞬间的洪峰,如何用「时间换空间」拉平**。

## 1. 为什么同步模型扛不住放票瞬间

放票瞬间,百万用户同时点「购票」:

- **同步模型的死亡螺旋**:请求到达 → 占用 Tomcat 线程 → 线程去查库/扣 Redis(毫秒~百毫秒)→ 期间线程被占 → 后续请求排队 → 线程池打满 → 响应更慢 → 用户超时重试(流量翻倍!)→ 螺旋恶化 → 服务雪崩
- 关键洞察:**用户的可接受等待时间和系统的处理能力不匹配**。用户能等 30 秒(排队页显示「预计 2 分钟」),但系统用「同步响应」去扛,等于要求每个请求毫秒级处理完——做不到就崩
- **排队的本质**:用时间换空间。把「请求洪峰」装进队列这个「蓄水池」,让消费者按自己的能力匀速处理——瞬时压力变成持续压力,峰值被拉平

## 2. 排队模型的用户契约(设计哲学)

- 提交即受理:下单接口**只做轻量校验 + 入队**,立即返回「排队凭证」(requestId),毫秒级响应
- 结果异步可查:用户轮询「排队状态」接口拿结果(排队中/成功+订单号/失败+原因)
- 这与余票查询「允许秒级延迟」是同一哲学:**把强一致收敛到写路径的最后一刻,用户侧用最终一致**

## 3. RocketMQ 存储原理:为什么它能扛亿级消息(面试核心)

### 3.1 CommitLog:所有消息顺序追加写一个文件

- **所有 topic 的所有消息,统一顺序追加到一个 CommitLog 文件**(1GB 一个,满了滚下一个)
- 为什么顺序写快:**磁盘顺序 IO ≈ 几百 MB/s,接近内存速度;随机 IO 只有几百次/秒(磁头寻道)**——顺序写把「磁盘慢」这个弱点绕开了
- 对比:MySQL 的 B+ 树写入是「定位页 + 随机写页」,RocketMQ 是「只往文件尾巴追加」——后者是磁盘最喜欢的姿势

### 3.2 ConsumeQueue:消费用的逻辑索引

- 顺序写解决了「写」,但消费时「找某个 topic 的消息」怎么办?CommitLog 里消息是混着的
- ConsumeQueue:每个 topic 每个队列一份索引文件,**每条 20 字节**(offset + size + tagCode)——消费时先查索引,再按 offset 去 CommitLog 定位消息
- 本质:**写路径一条线(顺序),读路径多条线(按 topic 索引)**——一个文件两种用途

### 3.3 刷盘策略:快与安全的取舍

- **异步刷盘**(默认):消息写进 OS 的 page cache 就返回成功,OS 稍后落盘——快,但宕机可能丢最后几百毫秒的消息
- **同步刷盘**:每条消息 fsync 落盘才返回——不丢,但吞吐显著下降
- 金融级订单场景选同步刷盘或「主从同步复制」;我们学习项目用异步刷盘 + 理解取舍即可

### 3.4 投递语义:At-Least-Once 与业务幂等

- RocketMQ 保证**至少一次投递**(消息不丢,但可能重复投递:消费者宕机/重平衡导致同一条消息被消费两次)
- 因此**消费者必须幂等**——我们的幂等靠 seat_hold 唯一索引:**重复消费 → 插凭证撞唯一键 → 当作已处理跳过**(阶段一决策六的第三次实战,这次在消息消费场景)
- 面试金句:MQ 只承诺至少一次,**恰好一次(Exactly-Once)是业务层的责任**

### 3.5 延迟消息(阶段六的载体,先了解原理)

- RocketMQ 内置 **18 个延迟等级**(1s/5s/10s/30s/1m/.../2h),发送时指定等级,bBroker 暂存到点再投递
- 实现:延迟消息先进「延迟队列」,定时任务到点把它转进真实队列
- 阶段六「下单 30 分钟未支付自动取消」就靠它:下单成功后发一条 30 分钟延迟消息,到点消费者执行取消+回补

## 4. 改造架构

```
用户下单 POST /api/order/create
  → 轻量校验 → 生成 requestId(雪花)→ 排队表插一条(status=排队中)
  → 消息体(请求 JSON)发 RocketMQ topic ORDER_CREATE
  → 立即返回 {requestId}                      ← 毫秒级响应,洪峰被接住

消费者(顺序消费):
  → 反序列化消息体 → 执行原 createOrder 十步(幂等/懒加载/Lua扣减/建单/失效缓存)
  → 回写排队表:成功(orderId)/ 失败(原因)

用户轮询 GET /api/order/status?requestId=
  → 排队中 / 成功+orderId / 失败+原因
```

**排队表** `order_request`:

```sql
CREATE TABLE order_request (
  id          BIGINT NOT NULL,
  request_id  VARCHAR(32) NOT NULL COMMENT '排队凭证(对用户展示)',
  user_id     BIGINT NOT NULL,
  payload     TEXT NOT NULL COMMENT '原始下单请求 JSON(消费者要反序列化)',
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0排队中 1成功 2失败',
  order_id    BIGINT DEFAULT NULL COMMENT '成功后的订单ID',
  fail_reason VARCHAR(200) DEFAULT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_request (request_id)
) COMMENT='下单排队表';
```

## 5. 任务清单

1. **Docker 起 RocketMQ**:namesrv(9876)+ broker(10911);apache/rocketmq 镜像
2. **依赖**:`rocketmq-spring-boot-starter`(2.3.x,与 Boot 3 兼容版注意)
3. **排队表 DDL + OrderRequest 实体 + Mapper**
4. **生产者改造**:OrderService.createOrder → 轻量校验 → requestId → 插排队表 → 发消息 → 返回 requestId;原十步逻辑抽成 `executeOrder(payload)`(消费者复用)
5. **消费者**:`OrderConsumer`(顺序消费 MessageListenerOrderly)→ 反序列化 → executeOrder → 回写排队表(成功/失败)
6. **查询接口**:`GET /api/order/status?requestId=`
7. **验收**:
   - 下单立即返回 requestId(毫秒级)
   - 轮询 status:排队中 → 成功+orderId
   - 余票不足:status → 失败+原因
   - **洪峰测试**:100 个并发下单 → 全部立即受理,消费者匀速处理,库存不超卖

## 6. 面试自查

- 为什么放票瞬间同步模型会崩?「死亡螺旋」怎么讲?
- 排队的本质是什么?用户契约是什么?
- CommitLog 为什么顺序写?和 MySQL B+ 树写入的本质区别?
- ConsumeQueue 解决什么问题?20 字节是什么?
- 同步刷盘和异步刷盘的取舍?订单场景选哪个?
- At-Least-Once 是什么意思?为什么消费者必须幂等?你的幂等靠什么?
- 延迟消息怎么实现的?18 个等级是什么?
- 顺序消费和并发消费的区别?削峰场景选哪个?
