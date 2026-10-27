package com.ticket.train.entity;

import java.time.LocalTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName ("train")
public class Train {
    /** 主键,雪花ID */
    @TableId (type = IdType.ASSIGN_ID)
    private Long id;
    /** 车次号,如 G101 */
    private String trainNo;
    /** 车型:1高铁 2动车 3普速 */
    private Integer trainType;
    /** 始发站ID(站名的权威在 station 表,这里只存 id) */
    private Long startStationId;
    /** 终到站ID */
    private Long endStationId;
    /** 始发时间 */
    private LocalTime departTime;
    /** 终到时间 */
    private LocalTime arriveTime;
    /** 开行状态:1开行 0停运(查询过滤 + 下单校验都用它) */
    private Integer status;
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
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
    public Long getStartStationId() {
        return startStationId;
    }
    public void setStartStationId(Long startStationId) {
        this.startStationId = startStationId;
    }
    public Long getEndStationId() {
        return endStationId;
    }
    public void setEndStationId(Long endStationId) {
        this.endStationId = endStationId;
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
    public Integer getStatus() {
        return status;
    }
    public void setStatus(Integer status) {
        this.status = status;
    }
}
