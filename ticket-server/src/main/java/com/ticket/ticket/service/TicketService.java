package com.ticket.ticket.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ticket.common.exception.BizException;
import com.ticket.ticket.cache.StockCache;
import com.ticket.ticket.dto.StockInfoResponse;
import com.ticket.ticket.mapper.TrainSeatStockMapper;

@Service 
public class TicketService {
    private final TrainSeatStockMapper stockMapper;
    private final StockCache stockCache;
    public TicketService(TrainSeatStockMapper stockMapper,StockCache stockCache) {
        this.stockMapper = stockMapper;
        this.stockCache=stockCache;
    }
    public List<StockInfoResponse> seatstock(Long trainId,LocalDate date)
    {
        if (date.isBefore(LocalDate.now())) {
            throw new BizException(400, "只能查询今天及以后的余票");
        }
        String key="stock:"+trainId+":"+date;
        return stockCache.get(key,()->stockMapper.selectStockInfo(trainId,date));
    }
}
