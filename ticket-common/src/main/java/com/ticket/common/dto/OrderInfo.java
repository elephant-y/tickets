package com.ticket.common.dto;

import java.math.BigDecimal;

/**
 * 跨服务 DTO:订单信息(pay-service 经 Feign 查 order-service 做支付预检)。
 * 只含支付需要的字段:存在性/归属/状态/金额 —— 最小契约,不泄露订单域内部细节。
 */
public class OrderInfo {

    private Long id;

    /** 下单用户(归属校验用) */
    private Long userId;

    /** 订单状态:10待支付 20已支付 30已出票 40已取消 50已退票 */
    private Integer status;

    /** 订单金额(支付单金额来源) */
    private BigDecimal totalAmount;

    public OrderInfo() {
    }

    public OrderInfo(Long id, Long userId, Integer status, BigDecimal totalAmount) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
