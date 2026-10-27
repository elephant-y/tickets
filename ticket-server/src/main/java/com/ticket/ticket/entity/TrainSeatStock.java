package com.ticket.ticket.entity;

import java.time.LocalDate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName ("train_seat_stock")
public class TrainSeatStock {
    @TableId (type = IdType.ASSIGN_ID)
    private Long id;
    private Long trainId;
    /** 开行日期 */
    private LocalDate trainDate;
    private Long seatTypeId;
    /** 起始站序 */
    private Integer fromStationNo;
    /** 到达站序 */
    private Integer toStationNo;
    /** 该区间票额(配置,简化为总座位数) */
    private Integer totalSeat;
    /** 已售计数(事实),余票=total-seat 为派生值 */
    private Integer soldSeat;
    /** 乐观锁版本号 */
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getFromStationNo() {
        return fromStationNo;
    }

    public void setFromStationNo(Integer fromStationNo) {
        this.fromStationNo = fromStationNo;
    }

    public Integer getToStationNo() {
        return toStationNo;
    }

    public void setToStationNo(Integer toStationNo) {
        this.toStationNo = toStationNo;
    }

    public Integer getTotalSeat() {
        return totalSeat;
    }

    public void setTotalSeat(Integer totalSeat) {
        this.totalSeat = totalSeat;
    }

    public Integer getSoldSeat() {
        return soldSeat;
    }

    public void setSoldSeat(Integer soldSeat) {
        this.soldSeat = soldSeat;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
