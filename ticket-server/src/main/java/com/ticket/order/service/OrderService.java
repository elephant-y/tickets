package com.ticket.order.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.exception.BizException;
import com.ticket.order.dto.CreateOrderRequest;
import com.ticket.order.dto.CreateOrderRequest.Passenger;
import com.ticket.order.dto.OrderMessage;
import com.ticket.order.entity.Order;
import com.ticket.order.entity.OrderItem;
import com.ticket.order.entity.OrderRequest;
import com.ticket.order.entity.SeatHold;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.mapper.OrderRequestMapper;
import com.ticket.order.mapper.SeatHoldMapper;
import com.ticket.ticket.cache.StockCache;
import com.ticket.ticket.entity.TrainSeatStock;
import com.ticket.ticket.mapper.TrainSeatStockMapper;
import com.ticket.common.dto.StationInfo;
import com.ticket.order.client.TrainInnerClient;
import org.apache.rocketmq.spring.core.RocketMQTemplate;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final SeatHoldMapper seatHoldMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final TrainSeatStockMapper trainSeatStockMapper;
    private final TrainInnerClient trainInnerClient;
    private final OrderRequestMapper orderRequestMapper;
    private final ObjectMapper objectMapper;
    @Autowired 
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private StringRedisTemplate redis;
    private final StockCache stockCache;
    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();
    static {
        DEDUCT_SCRIPT.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        DEDUCT_SCRIPT.setResultType(Long.class);
        REFUND_SCRIPT.setLocation(new ClassPathResource("lua/stock_refund.lua"));
        REFUND_SCRIPT.setResultType(Long.class);
    }
    @Autowired 
    private TransactionTemplate transactionTemplate;
    @Value("${ticket.order.timeout-level:16}")
    private int timeoutLevel;
    public OrderService(SeatHoldMapper seatHoldMapper,OrderMapper orderMapper,OrderItemMapper orderItemMapper,TrainSeatStockMapper trainSeatStockMapper,TrainInnerClient trainInnerClient,StockCache stockCache,OrderRequestMapper orderRequestMapper,ObjectMapper objectMapper)
    {
        this.seatHoldMapper=seatHoldMapper;
        this.orderMapper=orderMapper;
        this.orderItemMapper=orderItemMapper;
        this.trainSeatStockMapper=trainSeatStockMapper;
        this.trainInnerClient=trainInnerClient;
        this.stockCache=stockCache;
        this.orderRequestMapper=orderRequestMapper;
        this.objectMapper=objectMapper;
    }
    public String createOrder(CreateOrderRequest req,Long userId) 
    {
        validate(req);
        String requestId = generateOrderNo();
        OrderRequest record=new OrderRequest();
        record.setRequestId(requestId);
        record.setUserId(userId);
        try {
            record.setPayload(objectMapper.writeValueAsString(req));
        } catch (Exception e) {
            log.error("下单请求序列化失败", e);
            throw new BizException(500, "下单失败,请重试");
        }  // 原始请求序列化
        record.setStatus(0);
        orderRequestMapper.insert(record);
        rocketMQTemplate.convertAndSend("ORDER_CREATE",
            new OrderMessage(requestId, userId, req));
        return requestId;
    }
    public Long executeOrder(CreateOrderRequest req, Long userId) 
    {
        int fromNo = resolveStationNo(req.getTrainId(), req.getFromStationId());
        int toNo   = resolveStationNo(req.getTrainId(), req.getToStationId());
        List<Long> holdIds=holdSeats(req);
        List<int[]> intervals = overlapIntervals(req, fromNo, toNo);
        List<String> keys = intervals.stream()
                .map(iv -> stockKey(req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(), iv[0], iv[1]))
                .toList();
        String trainNo = trainInnerClient.getTrainInfo(req.getTrainId()).getData().getTrainNo();   // 冗余车次号(Feign)
        BigDecimal amount = calcAmount(req);                                      // 事务外算好传入
        boolean deducted=false;
        try {
        // ④ 懒加载库存 key(不存在 → 从 DB 读 total-sold 写入)
            ensureStockKeys(req);

        // ⑤ 算重叠区间 keys → Lua 扣减(-2 → 409)

            deduct(req,keys, req.getPassengers().size());
            deducted=true;
        // ⑥ 建订单 + 明细(编程式事务只包这一步)
            Long orderId = saveOrderAndItems(req, userId, fromNo, toNo, holdIds, intervals, trainNo, amount);
            sendTimeoutMessage(orderId);
        // ⑦ 删除余票缓存(本地 + Redis)
            invalidateCache(req);
            return orderId;
        } catch (Exception e) {
        // ⑧ 任何失败 → 回补库存 + 删除 seat_hold(占座与释放成对)
            if(deducted) refundQuietly(keys, req.getPassengers().size());
            releaseHolds(holdIds);
            throw e;
        }
    }
    /** 取消订单(10 待支付 → 40 已取消):超时闹钟/对账补刀/用户主动,三个入口共用 */
    public boolean cancelOrder(Long orderId, Long userId) {
        return migrateOrder(orderId, userId, 10, 40);
    }

    /** 退票(20 已支付 → 50 已退票):座位回流链路与取消完全相同;退款(钱)待阶段七支付模块 */
    public boolean refundOrder(Long orderId, Long userId) {
        return migrateOrder(orderId, userId, 20, 50);
    }

    /** 订单状态迁移(取消/退票共用的回流链路):入口校验 → 事务内三件事 → 事务外回补 */
    private boolean migrateOrder(Long orderId, Long userId, int fromStatus, int toStatus) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new BizException(404, "订单不存在");
        if (order.getStatus() != fromStatus) {
            return false;      // 状态机幂等入口:非目标状态 → 不做任何事(闹钟作废/重复操作拦截)
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作该订单");
        }
        // ── 事务外准备:站序换算 + 重叠区间(回补和失效都要用)──
        int fromNo = resolveStationNo(order.getTrainId(), order.getFromStationId());
        int toNo = resolveStationNo(order.getTrainId(), order.getToStationId());
        List<int[]> intervals = overlapIntervals(order.getTrainId(), order.getTrainDate(),
                order.getSeatTypeId(), fromNo, toNo);
        List<String> keys = intervals.stream()
                .map(iv -> stockKey(order.getTrainId(), order.getTrainDate(),
                        order.getSeatTypeId(), iv[0], iv[1]))
                .toList();

        Boolean done = transactionTemplate.execute(status -> {
            // ① 状态迁移(WHERE status=fromStatus 条件更新 ← 防并发重复操作,行锁保证只有一个成功)
            int affected = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, orderId)
                    .eq(Order::getStatus, fromStatus)
                    .set(Order::getStatus, toStatus)
                    .set(Order::getCancelTime, LocalDateTime.now()));
            if (affected == 0) return false;      // 并发下已被处理 → 本次什么都不做

            // ② DB sold 回减:每重叠区间 -count(下单 updateSold 的反向,真相层同步)
            for (int[] iv : intervals) {
                trainSeatStockMapper.decreaseSold(order.getTrainId(), order.getTrainDate(),
                        order.getSeatTypeId(), iv[0], iv[1], order.getTicketCount());
            }

            // ③ 删凭证:按 orderId 删(阶段四回填过 orderId)——乘车人可以重新购买
            seatHoldMapper.delete(new LambdaQueryWrapper<SeatHold>()
                    .eq(SeatHold::getOrderId, orderId));
            return true;
        });

        if (!Boolean.TRUE.equals(done)) {
            return false;      // 并发下已被处理,幂等返回
        }

        // ── 事务外:Redis 回补 + 失效查询缓存(和下单补偿同款)──
        refundQuietly(keys, order.getTicketCount());
        stockCache.invalidate("stock:" + order.getTrainId() + ":" + order.getTrainDate());
        return true;
    }
    private void sendTimeoutMessage(Long orderId) {
        try {
            // 用 Spring Message + 四参 syncSend(该重载专为延迟消息设计):
            // 若用原生 Message,starter 转换器会把整个 Message 对象 JSON 化,消费者拿到的是
            // {"topic":...,"body":"base64"} 而不是纯文本 —— 实测踩坑(NumberFormatException)
            Message<String> msg = MessageBuilder.withPayload(String.valueOf(orderId)).build();
            rocketMQTemplate.syncSend("ORDER_TIMEOUT", msg, 3000, timeoutLevel);
        } catch (Exception e) {
            log.error("延迟消息发送失败,orderId={},待对账兜底", orderId, e);
        }
    }
    private Long saveOrderAndItems(CreateOrderRequest req, Long userId, int fromNo, int toNo,
                                   List<Long> holdIds, List<int[]> intervals,
                                   String trainNo, BigDecimal amount) {
        return transactionTemplate.execute(status -> {
            // ===== ① 建订单 =====
            Order order = new Order();
            order.setOrderNo(generateOrderNo());            // 业务订单号:T+时间+随机
            order.setUserId(userId);                        // 网关注入的用户
            order.setTrainId(req.getTrainId());
            order.setTrainNo(trainNo);                      // 冗余车次号(事务外查好传入)
            order.setTrainDate(req.getTrainDate());
            order.setFromStationId(req.getFromStationId());
            order.setToStationId(req.getToStationId());
            order.setSeatTypeId(req.getSeatTypeId());
            order.setTicketCount(req.getPassengers().size());
            order.setTotalAmount(amount);                   // 事务外算好传入(事务内只写不算)
            order.setStatus(10);                            // 10 = 待支付,状态机起点
            // payTime / cancelTime 留空:未支付、未取消
            orderMapper.insert(order);                      // 雪花 id 回填

            // ===== ② 循环插明细(一单一票:票数 = 乘车人数) =====
            for (Passenger p : req.getPassengers()) {
                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());      // ① 回填的雪花 id
                item.setUserId(userId);              // 分片键冗余:与 orders 同键同片(阶段八绑定表)
                item.setPassengerName(p.getName());
                item.setIdCard(p.getIdCard());
                // seatNo 留空:计数模型阶段不分配座位,出票时回填
                orderItemMapper.insert(item);
            }

            // ===== ③ 同步 sold:每个重叠区间一行,返回 0 = DB 兜底触发 → 抛异常整体回滚 =====
            for (int[] iv : intervals) {
                int affected = trainSeatStockMapper.updateSold(
                        req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(),
                        iv[0], iv[1], req.getPassengers().size());
                if (affected == 0) {
                    throw new BizException(409, "余票不足");   // 数据已漂移,拒绝并回滚
                }
            }

            // ===== ④ 回填凭证:占位 orderId 0 → 真实订单号(占座与订单挂钩) =====
            seatHoldMapper.update(null, new LambdaUpdateWrapper<SeatHold>()
                    .in(SeatHold::getId, holdIds)
                    .set(SeatHold::getOrderId, order.getId()));

            return order.getId();
        });
    }
    private void deduct(CreateOrderRequest req, List<String> keys, int count) {
        Long result = redis.execute(DEDUCT_SCRIPT, keys, String.valueOf(count));
        if (result != null && result == -1) {
            ensureStockKeys(req);                    // key 缺失:补一次初始化
            result = redis.execute(DEDUCT_SCRIPT, keys, String.valueOf(count));
        }
        if (result == null || result == -1) {
            throw new BizException(500, "库存数据异常,请稍后重试");   // 重试仍失败 = 数据有问题,别无限重试
        }
        if (result == -2) {
            throw new BizException(409, "余票不足");
        }
    }
    /** 补偿:回补库存(refund 脚本)。吞掉自己的异常(不覆盖原始异常),打 error —— 账没还上由阶段六对账兜底 */
    private void refundQuietly(List<String> keys, int count) {
        try {
            redis.execute(REFUND_SCRIPT, keys, String.valueOf(count));
        } catch (Exception e) {
            log.error("库存回补失败,待对账: keys={}, count={}", keys, count, e);
        }
    }

    /** 补偿:删除本次插入的占座凭证(占座与释放成对;删不存在的行无害,幂等) */
    private void releaseHolds(List<Long> holdIds) {
        if (holdIds == null || holdIds.isEmpty()) {
            return;
        }
        seatHoldMapper.deleteBatchIds(holdIds);
    }

    /**
     * 失效余票读缓存(变更驱动,阶段三伏笔兑现)。
     * 注意:这里失效的是「查询缓存 key」(车次+日期整体 18 行),不是库存 key——
     * 库存 key 被删反而要懒加载重搬,纯添乱。
     */
    private void invalidateCache(CreateOrderRequest req) {
        String queryCacheKey = "stock:" + req.getTrainId() + ":" + req.getTrainDate();
        stockCache.invalidate(queryCacheKey);
    }

    /** 重叠区间计算:返回 (x, y) 数字对 —— 两个消费者:拼 key 扣 Redis、按区间同步 DB sold */
    /** 请求版:委托给参数版(单一数据源:重叠计算只写一份) */
    private List<int[]> overlapIntervals(CreateOrderRequest req, int fromNo, int toNo) {
        return overlapIntervals(req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(), fromNo, toNo);
    }

    /** 参数版:下单与取消共用(经停列表改走 Feign 调 train-service) */
    private List<int[]> overlapIntervals(Long trainId, LocalDate date, Long seatTypeId, int fromNo, int toNo) {
        List<StationInfo> stations = trainInnerClient.listStations(trainId).getData();
        List<int[]> intervals = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            for (int j = i + 1; j < stations.size(); j++) {
                int x = stations.get(i).getStationNo();
                int y = stations.get(j).getStationNo();
                if (x <= toNo && y >= fromNo) {      // 重叠判定(阶段一决策的公式)
                    intervals.add(new int[]{x, y});
                }
            }
        }
        return intervals;
    }

    /** 金额(简化):票数 × 区间里程 × 0.5元/km;seat_type 倍率 TODO 后续接入 */
    private BigDecimal calcAmount(CreateOrderRequest req) {
        int fromMileage = resolveMileage(req.getTrainId(), req.getFromStationId());
        int toMileage = resolveMileage(req.getTrainId(), req.getToStationId());
        double yuan = req.getPassengers().size() * (toMileage - fromMileage) * 0.5;
        return BigDecimal.valueOf(yuan);
    }

    /** 里程查询(Feign 版):与 resolveStationNo 同源,守门逻辑在 train-service 的 404 里 */
    private int resolveMileage(Long trainId, Long stationId) {
        return trainInnerClient.getStation(trainId, stationId).getData().getMileage();
    }
    private String stockKey(Long trainId, LocalDate date, Long seatTypeId, int fromNo, int toNo) {
        return "stock:" + trainId + ":" + date + ":" + seatTypeId + ":" + fromNo + ":" + toNo;
    }
    private void ensureStockKeys(CreateOrderRequest req) {
        List<TrainSeatStock> rows = trainSeatStockMapper.selectList(
            new LambdaQueryWrapper<TrainSeatStock>()
                    .eq(TrainSeatStock::getTrainId, req.getTrainId())
                    .eq(TrainSeatStock::getTrainDate, req.getTrainDate())
                    .eq(TrainSeatStock::getSeatTypeId, req.getSeatTypeId()));
        if (rows.isEmpty()) {
            throw new BizException(400, "该日期没有可售票库存");
        }
         for (TrainSeatStock row : rows) {
        String key = stockKey(req.getTrainId(), req.getTrainDate(),
                req.getSeatTypeId(), row.getFromStationNo(), row.getToStationNo());
        int remain = row.getTotalSeat() - row.getSoldSeat();   // 剩余 = 票额 - 已售
        redis.opsForValue().setIfAbsent(key, String.valueOf(remain));
    }
    }
    private List<Long> holdSeats(CreateOrderRequest req) {
        List<Long> insertedIds = new ArrayList<>(); 
        try {
             for(Passenger p:req.getPassengers())
            {
                SeatHold hold=new SeatHold();
                hold.setOrderId(0L);
                hold.setIdCard(p.getIdCard());
                hold.setTrainId(req.getTrainId());
                hold.setTrainDate(req.getTrainDate());
                seatHoldMapper.insert(hold);
                insertedIds.add(hold.getId());
            }
        } catch (DuplicateKeyException e) {
            if (!insertedIds.isEmpty()) {
            seatHoldMapper.deleteBatchIds(insertedIds);
        }
        throw new BizException(409, "乘车人已购同车次同日车票");
        }
        return insertedIds;
    }
    /** ① 参数校验:每个不合法项一个明确的 400 */
    private void validate(CreateOrderRequest req) {
        if (req.getPassengers() == null || req.getPassengers().isEmpty()
                || req.getPassengers().size() > 5) {
            throw new BizException(400, "乘车人数量必须为 1~5");
        }
        if (req.getTrainDate() == null || req.getTrainDate().isBefore(LocalDate.now())) {
            throw new BizException(400, "只能购买今天及以后的车票");
        }
        if (req.getFromStationId() == null || req.getToStationId() == null
                || req.getFromStationId().equals(req.getToStationId())) {
            throw new BizException(400, "出发站和到达站不能为空且不能相同");
        }
    }

    /**
     * 订单号生成(方案A):业务标识 T + 下单时间到秒 + 6 位随机数。
     * 与雪花主键解耦:业务编号给人看(时间可读),主键给机器用;随机后缀防遍历,
     * 同一秒撞号的概率由 uk_order_no 唯一索引兜底。
     */
    private String generateOrderNo() {
        return "T" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** ② 站序换算(Feign 版):stationId → stationNo;合法性守门在 train-service(查不到 404) */
    private int resolveStationNo(Long trainId, Long stationId) {
        return trainInnerClient.getStation(trainId, stationId).getData().getStationNo();
    }
}
