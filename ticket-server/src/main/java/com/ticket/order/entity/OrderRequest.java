package com.ticket.order.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 下单排队记录(削峰的核心数据):
 * 入队时插一条「排队中」,消费者处理完回写终态(成功/失败),用户轮询它拿结果。
 * uk_request 唯一索引:requestId 是排队凭证,全局唯一。
 */
@TableName("order_request")
public class OrderRequest {
    /** 主键,雪花ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 排队凭证(对用户展示,用户轮询用) */
    private String requestId;
    /** 下单用户 */
    private Long userId;
    /** 原始下单请求 JSON(消费者反序列化执行) */
    private String payload;
    /** 状态:0排队中 1成功 2失败 */
    private Integer status;
    /** 成功后的订单ID */
    private Long orderId;
    /** 失败原因 */
    private String failReason;
    /** 创建时间(DB 默认值) */
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
