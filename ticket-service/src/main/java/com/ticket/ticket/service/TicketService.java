package com.ticket.ticket.service;

import java.time.LocalDate;
import java.util.List;

import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ticket.common.dto.StationInfo;
import com.ticket.common.exception.BizException;
import com.ticket.ticket.cache.StockCache;
import com.ticket.ticket.client.TrainInnerClient;
import com.ticket.ticket.dto.StockInfoResponse;
import com.ticket.ticket.mapper.TrainSeatStockMapper;

@Service
public class TicketService {
    private final TrainSeatStockMapper stockMapper;
    private final StockCache stockCache;
    private final TrainInnerClient trainInnerClient;
    public TicketService(TrainSeatStockMapper stockMapper, StockCache stockCache, TrainInnerClient trainInnerClient) {
        this.stockMapper = stockMapper;
        this.stockCache = stockCache;
        this.trainInnerClient = trainInnerClient;
    }

    /**
     * 余票查询(12306 语义):返回「出发站→到达站」这一个区间的各座位类型余票(3 行)。
     * 缓存仍按车次+日期存全量 18 行(扣减失效逻辑不变),响应时按区间过滤;
     * 站序换算经 Feign 调 train-service(用户传的是站 id)。
     */
    public List<StockInfoResponse> seatstock(Long trainId, LocalDate date, Long fromStationId, Long toStationId)
    {
        if (date.isBefore(LocalDate.now())) {
            throw new BizException(400, "只能查询今天及以后的余票");
        }
        // 站 id → 站序(Feign;train-service 已做合法性守门)
        StationInfo from = trainInnerClient.getStation(trainId, fromStationId).getData();
        StationInfo to = trainInnerClient.getStation(trainId, toStationId).getData();

        String key = "stock:" + trainId + ":" + date;
        List<StockInfoResponse> all = stockCache.get(key, () -> stockMapper.selectStockInfo(trainId, date));
        // 过滤出查询区间的行(18 行 → 3 行:三种座位类型)
        return all.stream()
                .filter(s -> s.getFromStationNo() == from.getStationNo()
                        && s.getToStationNo() == to.getStationNo())
                .collect(Collectors.toList());
    }
}
