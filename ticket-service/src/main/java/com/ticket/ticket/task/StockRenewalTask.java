package com.ticket.ticket.task;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticket.ticket.mapper.TrainSeatStockMapper;

/**
 * 库存续期:每天 0 点生成「第 31 天」的区间库存,保证可售票窗口永远是未来 30 天。
 * 与 init-data.sql 的初始化 SQL 同源(经停表自连接 × 座位类型),id 用 UUID_SHORT 占位。
 */
@Component
public class StockRenewalTask {

    private static final Logger log = LoggerFactory.getLogger(StockRenewalTask.class);

    private final TrainSeatStockMapper trainSeatStockMapper;

    public StockRenewalTask(TrainSeatStockMapper trainSeatStockMapper) {
        this.trainSeatStockMapper = trainSeatStockMapper;
    }

    @Scheduled(cron = "0 0 0 * * ?")   // 每天 00:00 执行
    public void renewTomorrowStock() {
        LocalDate date = LocalDate.now().plusDays(30);   // 第 31 天(窗口永远覆盖未来 30 天)
        int rows = trainSeatStockMapper.renewStockForDate(date);
        log.info("库存续期完成: {} 生成 {} 行区间库存", date, rows);
    }
}
