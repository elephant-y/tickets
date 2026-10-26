package com.ticket.common.id;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 雪花算法测试。核心思路:所有测试都用「假时钟」(AtomicLong)精确控制时间,
 * 这样才能把时钟回拨、序列号溢出这些真实环境里几乎碰不到的场景测到。
 */
class SnowflakeIdGeneratorTest {

    /** 单毫秒序列号上限 = 2^12 */
    private static final int SEQUENCE_LIMIT = 4096;

    /** 时间戳段位移 = 12(序列号) + 10(机器ID),用于取出 ID 的时间戳部分 */
    private static final int TIMESTAMP_SHIFT = 22;

    // ---------- 唯一性 ----------

    @Test
    void singleThread100kIdsAreUnique() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            assertTrue(ids.add(generator.nextId()), "第 " + i + " 个 ID 重复");
        }
    }

    @Test
    void concurrentIdsAreUnique() throws InterruptedException {
        // 8 个线程共享同一个生成器 —— 同时验证唯一性和 synchronized 的并发正确性
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        int threads = 8;
        int perThread = 50_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS), "生成超时");
        pool.shutdown();
        assertEquals(threads * perThread, ids.size(), "存在并发重复 ID");
    }

    // ---------- 时钟回拨 ----------

    @Test
    void smallClockBackwardIsTolerated() {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1000);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(2, clock::get);
        long id1 = generator.nextId();
        clock.addAndGet(-2); // 回拨 2ms(≤ 阈值 5ms),应被容忍且不重复
        long id2 = generator.nextId();
        long id3 = generator.nextId();
        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
    }

    @Test
    void largeClockBackwardThrows() {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1000);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, clock::get);
        generator.nextId();
        clock.addAndGet(-10); // 回拨 10ms(> 阈值 5ms),应快速失败
        assertThrows(IllegalStateException.class, generator::nextId);
    }

    // ---------- 序列号溢出 ----------

    @Test
    void sequenceOverflowWaitsForNextMillis() throws InterruptedException {
        AtomicLong clock = new AtomicLong(SnowflakeIdGenerator.EPOCH + 1000);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(4, clock::get);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < SEQUENCE_LIMIT; i++) {
            ids.add(generator.nextId()); // 同一毫秒,序列号 0~4095 全部用尽
        }
        assertEquals(SEQUENCE_LIMIT, ids.size());

        // 时钟仍停在原毫秒:下一次调用应自旋等待,直到 50ms 后时钟被拨到下一毫秒
        Thread advancer = new Thread(() -> {
            try {
                Thread.sleep(50);
                clock.incrementAndGet();
            } catch (InterruptedException ignored) {
            }
        });
        advancer.start();

        long begin = System.nanoTime();
        long id = generator.nextId();
        long waitedMs = (System.nanoTime() - begin) / 1_000_000;

        assertTrue(waitedMs >= 30, "应阻塞等待时钟前进,实际等待 " + waitedMs + "ms");
        assertTrue(ids.add(id), "溢出后生成的 ID 与之前重复");
        advancer.join();
    }

    // ---------- 参数校验与结构 ----------

    @Test
    void invalidWorkerIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024));
    }

    @Test
    void timestampPartIsNonDecreasing() {
        // 验证「趋势递增」:ID 的时间戳部分单调不减(这是 InnoDB 主键插入有序的前提)
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5);
        long prev = generator.nextId();
        for (int i = 0; i < 10_000; i++) {
            long cur = generator.nextId();
            assertTrue((cur >> TIMESTAMP_SHIFT) >= (prev >> TIMESTAMP_SHIFT), "时间戳部分应单调不减");
            prev = cur;
        }
    }

    @Test
    void extractTimestampIsCloseToNow() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(6);
        long id = generator.nextId();
        long ts = SnowflakeIdGenerator.extractTimestamp(id);
        long now = System.currentTimeMillis();
        assertTrue(ts <= now && now - ts < 10_000, "解析出的生成时间应接近当前时间");
    }
}
