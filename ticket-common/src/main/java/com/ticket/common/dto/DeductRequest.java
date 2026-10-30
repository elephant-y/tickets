package com.ticket.common.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 跨服务 DTO:库存扣减/回补请求(order 域经 Feign 调 ticket-service)。
 * 放 common:双方契约单一来源(与 StationInfo/TrainInfo 同理)。
 *
 * requestId 是幂等键:Feign 超时重试时,ticket-service 用它判断「是否已处理」,
 * 重试命中幂等键直接返回已处理,防止双扣(分布式系统里没有免费的恰好一次)。
 */
public class DeductRequest {

    /** 幂等键:一次业务操作一个(下单用排队号 requestId,取消用 orderId 派生) */
    private String requestId;

    private Long trainId;

    private LocalDate trainDate;

    private Long seatTypeId;

    private List<Interval> intervals;
    /** 票数 */
    private Integer count;

    public DeductRequest() {
    }

    public DeductRequest(String requestId, Long trainId, LocalDate trainDate, Long seatTypeId,
                         List<Interval> intervals, Integer count) {
        this.requestId = requestId;
        this.trainId = trainId;
        this.trainDate = trainDate;
        this.seatTypeId = seatTypeId;
        this.intervals=intervals;
        this.count = count;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public LocalDate getTrainDate() {
        return trainDate;
    }

    public void setTrainDate(LocalDate trainDate) {
        this.trainDate = trainDate;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public void setSeatTypeId(Long seatTypeId) {
        this.seatTypeId = seatTypeId;
    }

    

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public List<Interval> getIntervals() {
        return intervals;
    }

    public void setIntervals(List<Interval> intervals) {
        this.intervals = intervals;
    }
}
