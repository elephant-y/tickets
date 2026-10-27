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
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ticket.common.exception.BizException;
import com.ticket.order.dto.CreateOrderRequest;
import com.ticket.order.dto.CreateOrderRequest.Passenger;
import com.ticket.order.entity.Order;
import com.ticket.order.entity.OrderItem;
import com.ticket.order.entity.SeatHold;
import com.ticket.order.mapper.OrderItemMapper;
import com.ticket.order.mapper.OrderMapper;
import com.ticket.order.mapper.SeatHoldMapper;
import com.ticket.ticket.cache.StockCache;
import com.ticket.ticket.entity.TrainSeatStock;
import com.ticket.ticket.mapper.TrainSeatStockMapper;
import com.ticket.train.entity.TrainStation;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.mapper.TrainStationMapper;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final SeatHoldMapper seatHoldMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final TrainSeatStockMapper trainSeatStockMapper;
    private final TrainStationMapper trainStationMapper;
    private final TrainMapper trainMapper;
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
    public OrderService(SeatHoldMapper seatHoldMapper,OrderMapper orderMapper,OrderItemMapper orderItemMapper,TrainSeatStockMapper trainSeatStockMapper,TrainStationMapper trainStationMapper,TrainMapper trainMapper,StockCache stockCache)
    {
        this.seatHoldMapper=seatHoldMapper;
        this.orderMapper=orderMapper;
        this.orderItemMapper=orderItemMapper;
        this.trainSeatStockMapper=trainSeatStockMapper;
        this.trainStationMapper=trainStationMapper;
        this.trainMapper=trainMapper;
        this.stockCache=stockCache;
    }
    public Long createOrder(CreateOrderRequest req,Long userId)
    {
        validate(req);
        int fromNo = resolveStationNo(req.getTrainId(), req.getFromStationId());
        int toNo   = resolveStationNo(req.getTrainId(), req.getToStationId());
        List<Long> holdIds=holdSeats(req);
        List<int[]> intervals = overlapIntervals(req, fromNo, toNo);
        List<String> keys = intervals.stream()
                .map(iv -> stockKey(req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(), iv[0], iv[1]))
                .toList();
        String trainNo = trainMapper.selectById(req.getTrainId()).getTrainNo();   // 冗余车次号
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

        // ⑦ 失效余票缓存(本地 + Redis)
            invalidateCache(req);
            return orderId;
        } catch (Exception e) {
        // ⑧ 任何失败 → 回补库存 + 删除 seat_hold(占座与释放成对)
            if(deducted) refundQuietly(keys, req.getPassengers().size());
            releaseHolds(holdIds);
            throw e;
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
                item.setPassengerName(p.getName());
                item.setIdCard(p.getIdCard());
                // seatNo 留空:计数模型阶段不分配座位,出票时回填(阶段七)
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
    private List<int[]> overlapIntervals(CreateOrderRequest req, int fromNo, int toNo) {
        List<TrainStation> stations = trainStationMapper.selectList(
            new LambdaQueryWrapper<TrainStation>()
                    .eq(TrainStation::getTrainId, req.getTrainId())
                    .orderByAsc(TrainStation::getStationNo));
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

    /** 里程查询:与 resolveStationNo 同源,复用「站点合法性守门」逻辑 */
    private int resolveMileage(Long trainId, Long stationId) {
        TrainStation ts = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId, trainId)
                        .eq(TrainStation::getStationId, stationId));
        if (ts == null) {
            throw new BizException(400, "站点不在该车次的经停站中");
        }
        return ts.getMileage();
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

    /** ② 站序换算:stationId → stationNo(翻译 + 合法性守门,不信任前端传站序) */
    private int resolveStationNo(Long trainId, Long stationId) {
        TrainStation ts = trainStationMapper.selectOne(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId, trainId)
                        .eq(TrainStation::getStationId, stationId));
        if (ts == null) {
            throw new BizException(400, "站点不在该车次的经停站中");
        }
        return ts.getStationNo();
    }
}
