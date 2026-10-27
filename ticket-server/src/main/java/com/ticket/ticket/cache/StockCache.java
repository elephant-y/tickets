package com.ticket.ticket.cache;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ticket.ticket.dto.StockInfoResponse;



@Component
public class StockCache {
    private static final Logger log = LoggerFactory.getLogger(StockCache.class);

    /** 余票缓存基础 TTL(秒) */
    @Value("${ticket.cache.stock-ttl-seconds:30}")
    private int stockTtlSeconds;

    /** 空值标记 TTL(秒,防穿透;「不存在」的事实变化慢,但也要尽快让位给真数据) */
    @Value("${ticket.cache.null-ttl-seconds:60}")
    private int nullTtlSeconds;

    // ① 本地缓存:Caffeine,只设容量上限,不设过期(过期由 expireAt 承载)
    private final Cache<String, CacheEntry> local =
        Caffeine.newBuilder().maximumSize(1000).build();
    private final ConcurrentHashMap<String, Boolean> rebuilding = new ConcurrentHashMap<>();

    // ② 缓存条目:data + empty(空值标记)+ expireAt(逻辑过期时间)
    private static class CacheEntry {
        List<StockInfoResponse> data;
        boolean empty;
        long expireAt;

        CacheEntry(List<StockInfoResponse> data, boolean empty, long expireAt) {
            this.data = data;
            this.empty = empty;
            this.expireAt = expireAt;
        }
    }

    // ③ 核心方法:get(key, loader)
    public List<StockInfoResponse> get(String key, Supplier<List<StockInfoResponse>> loader) {
        // 命中且未过期 → 返回(empty 则返回空列表)
        // 命中但已过期 → 返回旧值 + 异步重建(防击穿的关键!)
        // miss → 同步查库 + 回填
        CacheEntry entry=local.getIfPresent(key);
        long now=System.currentTimeMillis();
        if(entry!=null&&entry.expireAt>now)
        {
            log.info("缓存命中: {}", key);
            return entry.data;
        }
        else if(entry!=null&&entry.expireAt<=now)
        {
            log.info("逻辑过期,返回旧值,异步重建: {}", key);
            asyncRebuild(key, loader);
            return entry.empty ? List.of() : entry.data;
        }
        else
        {
            // 命运A:缓存里没有 —— 同步查库(第一次查,无旧值可垫,必须等这一次)
            List<StockInfoResponse> data = loader.get();
            put(key, data);          // 回填(空列表也会被缓存,put 里处理 empty 标记)
            log.info("回源 DB,回填缓存: {}", key);
            return data;
        }
    }
    private void asyncRebuild(String key, Supplier<List<StockInfoResponse>> loader) {
    // putIfAbsent 返回 null = 黑板上之前没人 = 我抢到了重建权(原子操作)
    if (rebuilding.putIfAbsent(key, Boolean.TRUE) == null) {
        CompletableFuture.runAsync(() -> {
            try {
                put(key, loader.get());        // 查库 + 回填
            } catch (Exception e) {
               log.error("缓存重建失败: {}", key, e);   // 异步异常必须接住,否则静默丢失
            } finally {
                rebuilding.remove(key);        // 无论成败都要擦黑板
            }
        });
    }
    // 没抢到:什么都不做 —— 别人正在重建,我直接返回旧值
    }
    private void put(String key, List<StockInfoResponse> data) {
    boolean empty = data.isEmpty();
    // 空标记用短 TTL(60s),正常数据用基础 TTL(30s)
    int ttlSeconds = empty ? nullTtlSeconds : stockTtlSeconds;
    // ±10% 随机扰动(防雪崩):每个 key 的失效时刻打散
    long jitter = (long) (ttlSeconds * 1000L * (0.9 + Math.random() * 0.2));
    local.put(key, new CacheEntry(data, empty, System.currentTimeMillis() + jitter));
}

    // ④ 回填:empty 决定 TTL(空值用短 TTL),expireAt 加 ±10% 随机扰动
    // ⑤ 异步重建:CompletableFuture.runAsync + ConcurrentHashMap 标记「该 key 正在重建」,
    //    防止过期瞬间 1000 个请求同时查库(重建单飞)
}