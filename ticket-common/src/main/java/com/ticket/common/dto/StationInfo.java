package com.ticket.common.dto;

/**
 * 跨服务 DTO:站序+里程(供 order 域经 Feign 调用 train-service 获取)。
 * 为什么放 common:Feign 的调用方(ticket-server)和服务方(train-service)都要持有,
 * 放公共模块零复制、契约单一来源 —— 阶段二 JwtUtil 复制两份的教训,不再重演。
 */
public class StationInfo {

    /** 站序(车次内有序,重叠区间计算用) */
    private Integer stationNo;

    /** 距始发站里程(公里,票价计算用) */
    private Integer mileage;

    public StationInfo() {
    }

    public StationInfo(Integer stationNo, Integer mileage) {
        this.stationNo = stationNo;
        this.mileage = mileage;
    }

    public Integer getStationNo() {
        return stationNo;
    }

    public void setStationNo(Integer stationNo) {
        this.stationNo = stationNo;
    }

    public Integer getMileage() {
        return mileage;
    }

    public void setMileage(Integer mileage) {
        this.mileage = mileage;
    }
}
