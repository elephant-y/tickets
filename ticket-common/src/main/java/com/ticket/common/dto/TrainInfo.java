package com.ticket.common.dto;

/**
 * 跨服务 DTO:车次信息(供 order 域经 Feign 获取车次号冗余字段)。
 */
public class TrainInfo {

    private Long id;

    /** 车次号,如 G101 */
    private String trainNo;

    public TrainInfo() {
    }

    public TrainInfo(Long id, String trainNo) {
        this.id = id;
        this.trainNo = trainNo;
    }

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
}
