package com.ticket.order.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("orders")
public class Order {
    /** 主键,雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 业务订单号(对用户展示) */
    private String orderNo;
    /** 下单用户ID(网关透传) */
    private Long userId;
    /** 车次ID */
    private Long trainId;
    /** 冗余车次号,列表展示免JOIN(反规范化,阶段一的设计) */
    private String trainNo;
    /** 乘车日期 */
    private LocalDate trainDate;
    /** 出发站ID */
    private Long fromStationId;
    /** 到达站ID */
    private Long toStationId;
    /** 座位类型ID */
    private Long seatTypeId;
    /** 票数 1~5 */
    private Integer ticketCount;
    /** 订单金额(元),DECIMAL 对应 BigDecimal,禁止 float/double */
    private BigDecimal totalAmount;
    /** 状态:10待支付 20已支付 30已出票 40已取消 50已退票 */
    private Integer status;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 取消时间 */
    private LocalDateTime cancelTime;
    /** 创建时间(DB 默认值) */
    private LocalDateTime createTime;
    /** 更新时间(DB 默认值) */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public LocalDate getTrainDate() {
        return trainDate;
    }

    public void setTrainDate(LocalDate trainDate) {
        this.trainDate = trainDate;
    }

    public Long getFromStationId() {
        return fromStationId;
    }

    public void setFromStationId(Long fromStationId) {
        this.fromStationId = fromStationId;
    }

    public Long getToStationId() {
        return toStationId;
    }

    public void setToStationId(Long toStationId) {
        this.toStationId = toStationId;
    }

    public Long getSeatTypeId() {
        return seatTypeId;
    }

    public void setSeatTypeId(Long seatTypeId) {
        this.seatTypeId = seatTypeId;
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
