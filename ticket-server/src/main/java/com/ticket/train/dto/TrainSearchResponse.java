package com.ticket.train.dto;

import java.time.LocalTime;

/**
 * 车次查询响应(接口契约)。
 * 只返回前端展示需要的字段,与 Train 实体解耦——实体里存的是站 id,这里返回的是站名。
 */
public class TrainSearchResponse {

    /** 车次ID:前端后续查余票、下单时回传 */
    private Long trainId;

    /** 车次号,如 G101(注意是字符串:字母+数字,不是 Long) */
    private String trainNo;

    /** 车型:1高铁 2动车 3普速 */
    private Integer trainType;

    /** 始发站名(由 SQL JOIN station 表翻译而来,实体里只存 id) */
    private String startStationName;

    /** 终到站名(同上) */
    private String endStationName;

    /** 始发时间 */
    private LocalTime departTime;

    /** 终到时间 */
    private LocalTime arriveTime;

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public String getTrainNo() {
        return trainNo;
    }

    public void setTrainNo(String trainNo) {
        this.trainNo = trainNo;
    }

    public Integer getTrainType() {
        return trainType;
    }

    public void setTrainType(Integer trainType) {
        this.trainType = trainType;
    }

    public String getStartStationName() {
        return startStationName;
    }

    public void setStartStationName(String startStationName) {
        this.startStationName = startStationName;
    }

    public String getEndStationName() {
        return endStationName;
    }

    public void setEndStationName(String endStationName) {
        this.endStationName = endStationName;
    }

    public LocalTime getDepartTime() {
        return departTime;
    }

    public void setDepartTime(LocalTime departTime) {
        this.departTime = departTime;
    }

    public LocalTime getArriveTime() {
        return arriveTime;
    }

    public void setArriveTime(LocalTime arriveTime) {
        this.arriveTime = arriveTime;
    }
}
