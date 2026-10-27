package com.ticket.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("order_item")
public class OrderItem {
    /** 主键,雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 所属订单ID(1:N) */
    private Long orderId;
    /** 乘车人姓名 */
    private String passengerName;
    /** 乘车人证件号(幂等防重的关键字段之一) */
    private String idCard;
    /** 座位号,出票后回填(计数模型阶段为空) */
    private String seatNo;

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

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getSeatNo() {
        return seatNo;
    }

    public void setSeatNo(String seatNo) {
        this.seatNo = seatNo;
    }
}
