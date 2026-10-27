package com.ticket.order.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 占座凭证:同一乘车人同车次同日期仅一张(uk_hold 唯一索引)。
 * 占座插入、取消/退票删除 —— 用行生命周期对齐业务状态,复用唯一索引的原子冲突检测(阶段一的决策六)。
 */
@TableName("seat_hold")
public class SeatHold {
    /** 主键,雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 关联订单ID */
    private Long orderId;
    /** 乘车人证件号 */
    private String idCard;
    /** 车次ID */
    private Long trainId;
    /** 乘车日期 */
    private LocalDate trainDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
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
}
