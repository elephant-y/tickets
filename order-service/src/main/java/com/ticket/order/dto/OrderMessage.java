package com.ticket.order.dto;

/**
 * RocketMQ 下单消息体:消费者靠它知道「处理哪个请求、结果回写给谁」。
 * 三个字段缺一不可:
 * - requestId:排队凭证,消费者处理完按它回写排队表
 * - userId:网关注入的用户(消息跨进程,头信息带不过去,必须打包进消息体)
 * - req:原始下单请求(消费者反序列化后执行扣减)
 */
public class OrderMessage {

    private String requestId;
    private Long userId;
    private CreateOrderRequest req;

    /** 无参构造:框架反序列化需要 */
    public OrderMessage() {
    }

    public OrderMessage(String requestId, Long userId, CreateOrderRequest req) {
        this.requestId = requestId;
        this.userId = userId;
        this.req = req;
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

    public CreateOrderRequest getReq() {
        return req;
    }

    public void setReq(CreateOrderRequest req) {
        this.req = req;
    }
}
