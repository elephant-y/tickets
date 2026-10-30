package com.ticket.ticket.controller;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.dto.DeductRequest;
import com.ticket.common.dto.Interval;
import com.ticket.common.result.Result;
import com.ticket.ticket.cache.StockCache;
import com.ticket.ticket.entity.TrainSeatStock;
import com.ticket.ticket.mapper.TrainSeatStockMapper;

/**
 * 库存内部接口(order-service 经 Feign 调用的「仓库窗口」)。
 *
 * 幂等键(本接口的灵魂):Feign 超时重试会让同一请求到达两次 ——
 * SETNX 记录「requestId 已处理」,重试命中直接返回已处理,防止双扣/双退。
 * 扣与补的键分开(idem:deduct:/idem:refund:),TTL 10 分钟覆盖重试窗口;
 * 失败删键(允许重试),成功留键(挡重试)。
 */
@RestController
@RequestMapping("/api/inner/stock")
public class InnerStockController {

    private static final Logger log = LoggerFactory.getLogger(InnerStockController.class);

    /** 幂等键 TTL:覆盖 Feign 重试窗口,又不至于残留太久 */
    private static final Duration IDEM_TTL = Duration.ofMinutes(10);

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> REFUND_SCRIPT = new DefaultRedisScript<>();

    static {
        DEDUCT_SCRIPT.setLocation(new ClassPathResource("lua/stock_deduct.lua"));
        DEDUCT_SCRIPT.setResultType(Long.class);
        REFUND_SCRIPT.setLocation(new ClassPathResource("lua/stock_refund.lua"));
        REFUND_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;
    private final TrainSeatStockMapper trainSeatStockMapper;
    private final StockCache stockCache;

    public InnerStockController(StringRedisTemplate redis,
                               TrainSeatStockMapper trainSeatStockMapper,
                               StockCache stockCache) {
        this.redis = redis;
        this.trainSeatStockMapper = trainSeatStockMapper;
        this.stockCache = stockCache;
    }

    /** 扣减:幂等 → 懒加载 → Lua 原子扣 → DB 同步 → 失效缓存;失败回补 + 删键 */
    @PostMapping("/deduct")
    public Result<String> deduct(@RequestBody DeductRequest req) {
        String idemKey = "idem:deduct:" + req.getRequestId();
        List<String> keys = stockKeys(req);

        // ① 幂等:SETNX 抢到才算第一次,重试到达直接返回「已处理」
        Boolean first = redis.opsForValue().setIfAbsent(idemKey, "1", IDEM_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("扣减幂等命中(重试),直接返回: {}", req.getRequestId());
            return Result.success("已处理");
        }

        try {
            // ② 懒加载:每个区间 key 缺失时,从 DB 读 total-sold 写入(只搬缺失,绝不覆盖已扣值)
            ensureStockKeys(req);

            // ③ Lua 原子扣(-1 补初始化重试一次;-2 余票不足)
            Long result = redis.execute(DEDUCT_SCRIPT, keys, String.valueOf(req.getCount()));
            if (result != null && result == -1) {
                ensureStockKeys(req);
                result = redis.execute(DEDUCT_SCRIPT, keys, String.valueOf(req.getCount()));
            }
            if (result == null || result == -1) {
                throw new IllegalStateException("库存数据异常: 懒加载后仍缺 key");
            }
            if (result == -2) {
                return Result.error(409, "余票不足");
            }

            // ④ DB 同步:每区间 updateSold(0 行 = 兜底条件触发 = 数据漂移)
            for (Interval iv : req.getIntervals()) {
                int affected = trainSeatStockMapper.updateSold(
                        req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(),
                        iv.getFromNo(), iv.getToNo(), req.getCount());
                if (affected == 0) {
                    throw new IllegalStateException("DB 兜底条件触发,数据漂移");
                }
            }

            // ⑤ 失效查询缓存(变更驱动)
            stockCache.invalidate(queryCacheKey(req));

            log.info("扣减成功: {}", req.getRequestId());
            return Result.success("成功");
        } catch (Exception e) {
            // ⑥ 失败:回补 Redis(尽力而为)+ 删幂等键(允许 order 重试)
            log.error("扣减失败,回补并删除幂等键: {}", req.getRequestId(), e);
            try {
                redis.execute(REFUND_SCRIPT, keys, String.valueOf(req.getCount()));
            } catch (Exception ex) {
                log.error("失败回补也失败,待对账: {}", req.getRequestId(), ex);
            }
            redis.delete(idemKey);
            throw e;
        }
    }

    /** 回补:幂等 → Redis 加回 → DB 回减 → 失效缓存 */
    @PostMapping("/refund")
    public Result<String> refund(@RequestBody DeductRequest req) {
        String idemKey = "idem:refund:" + req.getRequestId();
        List<String> keys = stockKeys(req);

        Boolean first = redis.opsForValue().setIfAbsent(idemKey, "1", IDEM_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("回补幂等命中(重试),直接返回: {}", req.getRequestId());
            return Result.success("已处理");
        }

        try {
            // Redis 加回(refund 脚本:INCRBY,加回去永远安全)
            redis.execute(REFUND_SCRIPT, keys, String.valueOf(req.getCount()));

            // DB 回减(真相层)
            for (Interval iv : req.getIntervals()) {
                trainSeatStockMapper.decreaseSold(
                        req.getTrainId(), req.getTrainDate(), req.getSeatTypeId(),
                        iv.getFromNo(), iv.getToNo(), req.getCount());
            }

            stockCache.invalidate(queryCacheKey(req));
            log.info("回补成功: {}", req.getRequestId());
            return Result.success("成功");
        } catch (Exception e) {
            log.error("回补失败,删除幂等键待重试: {}", req.getRequestId(), e);
            redis.delete(idemKey);
            throw e;
        }
    }

    /** 懒加载(阶段四 ensureStockKeys 的搬家版):区间列表从请求来,不再查经停表 */
    private void ensureStockKeys(DeductRequest req) {
        List<TrainSeatStock> rows = trainSeatStockMapper.selectList(
                new LambdaQueryWrapper<TrainSeatStock>()
                        .eq(TrainSeatStock::getTrainId, req.getTrainId())
                        .eq(TrainSeatStock::getTrainDate, req.getTrainDate())
                        .eq(TrainSeatStock::getSeatTypeId, req.getSeatTypeId()));
        if (rows.isEmpty()) {
            throw new IllegalStateException("该日期没有可售票库存");
        }
        for (TrainSeatStock row : rows) {
            String key = stockKey(req.getTrainId(), req.getTrainDate(),
                    req.getSeatTypeId(), row.getFromStationNo(), row.getToStationNo());
            int remain = row.getTotalSeat() - row.getSoldSeat();
            // setIfAbsent:只搬缺失,绝不覆盖已扣值(否则已售票复活 = 超卖)
            redis.opsForValue().setIfAbsent(key, String.valueOf(remain));
        }
    }

    private List<String> stockKeys(DeductRequest req) {
        return req.getIntervals().stream()
                .map(iv -> stockKey(req.getTrainId(), req.getTrainDate(),
                        req.getSeatTypeId(), iv.getFromNo(), iv.getToNo()))
                .collect(Collectors.toList());
    }

    private String stockKey(Long trainId, LocalDate date, Long seatTypeId, int fromNo, int toNo) {
        return "stock:" + trainId + ":" + date + ":" + seatTypeId + ":" + fromNo + ":" + toNo;
    }

    /** 查询缓存 key(StockCache 层):车次+日期整体,与库存 key 两套体系 */
    private String queryCacheKey(DeductRequest req) {
        return "stock:" + req.getTrainId() + ":" + req.getTrainDate();
    }
}
