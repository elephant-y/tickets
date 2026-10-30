package com.ticket.pay.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 支付流水:一次支付行为的状态记录。
 * uk_pay_no:支付单号唯一;uk_order:一个订单只有一张支付单(重复点支付撞唯一键=幂等)。
 */
@TableName("pay_record")
public class PayRecord {
    /** 主键,雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 支付单号(对用户展示) */
    private String payNo;
    /** 关联订单 */
    private Long orderId;
    /** 支付金额(元) */
    private BigDecimal amount;
    /** 状态:0待支付 1成功 2失败 */
    private Integer status;
    /** 支付时间 */
    private LocalDateTime payTime;
    /** 创建时间(common AutoFillMetaObjectHandler 以 JVM 时钟填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPayNo() {
        return payNo;
    }

    public void setPayNo(String payNo) {
        this.payNo = payNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
